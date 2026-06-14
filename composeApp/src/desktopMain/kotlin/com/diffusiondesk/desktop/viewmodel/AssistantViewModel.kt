package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.AssistantChatResult
import com.diffusiondesk.desktop.core.AssistantToolPlan
import com.diffusiondesk.desktop.core.LlmChatMessage
import com.diffusiondesk.desktop.core.LlmPresetStore
import com.diffusiondesk.desktop.core.LlmRoleService
import com.diffusiondesk.desktop.core.effectiveAdvancedArgs
import com.diffusiondesk.desktop.core.inferLlmContextLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil
import java.time.LocalDateTime
import java.util.Base64
import javax.imageio.ImageIO

enum class AssistantMessageRole {
    User,
    Assistant,
    System,
    Tool,
}

data class AssistantMessage(
    val role: AssistantMessageRole,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val toolCall: AssistantToolCall? = null,
    val imageAttachment: AssistantImageAttachment? = null,
)

data class AssistantToolCall(
    val name: String,
    val input: String,
    val output: String,
    val success: Boolean,
)

data class AssistantImageAttachment(
    val name: String,
    val dataUri: String,
    val thumbnailDataUri: String,
    val width: Int? = null,
    val height: Int? = null,
)

data class AssistantContextSnapshot(
    val screen: String,
    val promptMode: String,
    val prompt: String,
    val negativePrompt: String,
    val width: String,
    val height: String,
    val steps: String,
    val cfgScale: String,
    val sampler: String,
    val seed: String,
    val selectedPreset: String,
    val compositionSummary: String,
    val selectedCompositionElement: String,
)

data class AssistantUiState(
    val messages: List<AssistantMessage> = listOf(
        AssistantMessage(
            role = AssistantMessageRole.Assistant,
            content = "Hello. I am your Creative Assistant. I can help refine prompts, reason about the current composition, and prepare edits.",
        ),
    ),
    val isLoading: Boolean = false,
    val lastUsageLabel: String? = null,
    val debugInfo: AssistantDebugInfo? = null,
    val pendingImage: AssistantImageAttachment? = null,
    val error: String? = null,
)

data class AssistantDebugInfo(
    val estimatedPromptTokens: Int,
    val contextLimitTokens: Int?,
    val contextLimitSource: String,
    val requestMessages: Int,
    val maxTokens: Int?,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val promptTokensPerSecond: Double? = null,
    val predictedTokensPerSecond: Double? = null,
    val elapsedMs: Long? = null,
    val finishReason: String? = null,
    val contentChars: Int = 0,
    val reasoningChars: Int = 0,
    val workerBaseUrl: String? = null,
    val presetName: String? = null,
    val advancedArgs: List<String> = emptyList(),
)

data class AssistantToolRequest(
    val tool: String,
    val description: String,
    val prompt: String = "",
    val negativePrompt: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val steps: Int? = null,
    val cfgScale: Double? = null,
    val seed: Int? = null,
    val sampler: String = "",
    val replaceExistingComposition: Boolean = false,
)

data class AssistantToolResult(
    val accepted: Boolean,
    val message: String,
)

class AssistantViewModel(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val llmPresetStore: LlmPresetStore,
    private val llmRoleService: LlmRoleService,
    private val latestImageProvider: () -> AssistantImageAttachment? = { null },
    private val runTool: suspend (AssistantToolRequest) -> AssistantToolResult = {
        AssistantToolResult(false, "Assistant tools are not connected.")
    },
) {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(AssistantUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<AssistantUiState> = _uiState

    private var activeJob: Job? = null

    fun sendMessage(text: String, context: AssistantContextSnapshot) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isLoading) return

        activeJob = scope.launch {
            val outgoingImage = _uiState.value.pendingImage
            val userMessage = AssistantMessage(
                role = AssistantMessageRole.User,
                content = trimmed,
                imageAttachment = outgoingImage,
            )
            update {
                copy(
                    messages = messages + userMessage,
                    isLoading = true,
                    pendingImage = null,
                    error = null,
                )
            }

            val requestMessages = buildAssistantAgentMessages(_uiState.value.messages, context)
            val settings = settingsStore.load()
            val presets = llmPresetStore.load()
            val roles = llmPresetStore.loadRoles()
            val selectedPreset = presets.firstOrNull { it.id == roles.assistantPresetId }
            val advancedArgs = selectedPreset?.effectiveArgsOrEmpty().orEmpty()
            val inferredLimit = inferLlmContextLimit(advancedArgs)
            update {
                copy(
                    debugInfo = AssistantDebugInfo(
                        estimatedPromptTokens = estimatePromptTokens(requestMessages),
                        contextLimitTokens = inferredLimit.tokens,
                        contextLimitSource = inferredLimit.source,
                        requestMessages = requestMessages.size,
                        maxTokens = null,
                        presetName = selectedPreset?.name,
                        advancedArgs = advancedArgs,
                    ),
                )
            }

            val toolRun = runAssistantAgentLoop(
                settings = settings,
                presets = presets,
                roles = roles,
                requestMessages = requestMessages,
            )
            if (toolRun.handled) {
                toolRun.assistantResult?.let { result ->
                    appendAssistantResult(result, requestMessages)
                    return@launch
                }
                update {
                    val finalMessage = toolRun.reply.ifBlank {
                        if (toolRun.success) "Done." else toolRun.errorMessage.ifBlank { "I could not complete that action." }
                    }
                    copy(
                        messages = messages + AssistantMessage(
                            role = if (toolRun.success) AssistantMessageRole.Assistant else AssistantMessageRole.System,
                            content = finalMessage,
                        ),
                        isLoading = false,
                        error = toolRun.errorMessage.takeUnless { toolRun.success },
                    )
                }
                return@launch
            }

            appendAssistantError("Assistant agent returned no response.")
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        update { copy(isLoading = false) }
    }

    fun clearHistory() {
        activeJob?.cancel()
        activeJob = null
        update {
            AssistantUiState(
                messages = listOf(
                    AssistantMessage(
                        role = AssistantMessageRole.Assistant,
                        content = "History cleared. How can I help you now?",
                    ),
                ),
            )
        }
    }

    fun attachImage(path: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = Path.of(path)
                    val bytes = Files.readAllBytes(file)
                    val mime = when (file.fileName.toString().substringAfterLast('.', "").lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "webp" -> "image/webp"
                        else -> "image/png"
                    }
                    val image = runCatching {
                        ImageIO.read(bytes.inputStream())
                    }.getOrNull()
                    val dataUri = "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
                    AssistantImageAttachment(
                        name = file.fileName.toString(),
                        dataUri = dataUri,
                        thumbnailDataUri = image?.toThumbnailDataUri() ?: dataUri,
                        width = image?.width,
                        height = image?.height,
                    )
                }
            }.onSuccess { attachment ->
                update { copy(pendingImage = attachment, error = null) }
            }.onFailure { error ->
                update {
                    copy(
                        messages = messages + AssistantMessage(
                            role = AssistantMessageRole.System,
                            content = "Image attachment failed: ${error.message ?: "Could not read image."}",
                        ),
                        error = error.message ?: "Could not read image.",
                    )
                }
            }
        }
    }

    fun clearAttachedImage() {
        update { copy(pendingImage = null) }
    }

    private fun update(transform: AssistantUiState.() -> AssistantUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private suspend fun runAssistantAgentLoop(
        settings: com.diffusiondesk.desktop.core.DesktopSettings,
        presets: List<com.diffusiondesk.desktop.core.LlmPreset>,
        roles: com.diffusiondesk.desktop.core.LlmRoleSettings,
        requestMessages: List<LlmChatMessage>,
    ): ToolLoopResult {
        val agentMessages = requestMessages.toMutableList()
        var handled = false
        var lastReply = ""
        var lastError = ""
        var allSucceeded = true
        var executedCalls = 0
        var lastAssistantResult: AssistantChatResult? = null

        repeat(MaxToolPlanningRounds) {
            val turn = llmRoleService.chatWithAssistantAgentDetailed(
                settings = settings,
                presets = presets,
                roles = roles,
                messages = agentMessages,
            ).getOrNull() ?: return@repeat
            lastAssistantResult = turn.result
            val batch = turn.batch
            val calls = batch.calls.take(MaxToolCallsPerTurn - executedCalls)
            if (calls.isEmpty()) {
                return ToolLoopResult(
                    handled = true,
                    success = allSucceeded && lastError.isBlank(),
                    reply = batch.reply.ifBlank { lastReply },
                    errorMessage = lastError,
                    assistantResult = turn.result.withDisplayContent(batch.reply.ifBlank { lastReply }),
                )
            }

            handled = true
            lastReply = batch.reply.ifBlank { lastReply }
            agentMessages += LlmChatMessage(
                role = "assistant",
                content = turn.result.completion.content,
                toolCalls = turn.result.completion.toolCalls,
            )
            for ((index, plan) in calls.withIndex()) {
                val toolRequest = plan.toToolRequest()
                val toolCallId = turn.result.completion.toolCalls.getOrNull(index)?.id ?: "call_$index"
                if (toolRequest.tool == "inspect_latest_image") {
                    val image = latestImageProvider()
                    appendPendingToolCall(toolRequest)
                    val toolResult = if (image == null) {
                        AssistantToolResult(false, "No generated image is available yet.")
                    } else {
                        attachImageToLastUserMessage(image)
                        AssistantToolResult(
                            accepted = true,
                            message = "Latest generated image attached to the assistant chat for direct analysis.",
                        )
                    }
                    updateLastToolCall(toolRequest, toolResult)
                    agentMessages += LlmChatMessage(
                        role = "tool",
                        content = toolResult.outputJson(),
                        toolCallId = toolCallId,
                    )
                    if (image != null) {
                        agentMessages += LlmChatMessage(
                            role = "user",
                            content = buildString {
                                append("The latest generated image is attached for direct visual inspection.")
                                val width = image.width
                                val height = image.height
                                if (width != null && height != null) {
                                    append(" Image size: $width x $height (${aspectLabel(width, height)}).")
                                }
                            },
                            imageDataUri = image.dataUri,
                        )
                    }
                    executedCalls++
                    if (!toolResult.accepted) {
                        allSucceeded = false
                        lastError = toolResult.message
                    }
                    if (executedCalls >= MaxToolCallsPerTurn) {
                        return ToolLoopResult(
                            handled = true,
                            success = allSucceeded && lastError.isBlank(),
                            reply = lastReply,
                            errorMessage = lastError,
                            assistantResult = lastAssistantResult.withDisplayContent(lastReply),
                        )
                    }
                    continue
                }
                appendPendingToolCall(toolRequest)
                val toolResult = runTool(toolRequest)
                updateLastToolCall(toolRequest, toolResult)
                agentMessages += LlmChatMessage(
                    role = "tool",
                    content = toolResult.outputJson(),
                    toolCallId = toolCallId,
                )
                executedCalls++
                if (!toolResult.accepted) {
                    allSucceeded = false
                    lastError = toolResult.message
                }
                if (executedCalls >= MaxToolCallsPerTurn) {
                    return ToolLoopResult(
                        handled = true,
                        success = allSucceeded && lastError.isBlank(),
                        reply = lastReply,
                        errorMessage = lastError,
                        assistantResult = lastAssistantResult.withDisplayContent(lastReply),
                    )
                }
            }
        }

        return ToolLoopResult(
            handled = true,
            success = handled && allSucceeded && lastError.isBlank(),
            reply = lastReply,
            errorMessage = lastError,
            assistantResult = lastAssistantResult?.withDisplayContent(lastReply),
        )
    }

    private fun appendPendingToolCall(toolRequest: AssistantToolRequest) {
        update {
            copy(
                messages = messages + AssistantMessage(
                    role = AssistantMessageRole.Tool,
                    content = "",
                    toolCall = AssistantToolCall(
                        name = toolRequest.tool,
                        input = toolRequest.inputJson(),
                        output = "Running...",
                        success = false,
                    ),
                ),
            )
        }
    }

    private fun updateLastToolCall(toolRequest: AssistantToolRequest, toolResult: AssistantToolResult) {
        update {
            copy(
                messages = messages.replaceLastToolCall(
                    name = toolRequest.tool,
                    input = toolRequest.inputJson(),
                    output = toolResult.outputJson(),
                    success = toolResult.accepted,
                ),
            )
        }
    }

    private fun attachImageToLastUserMessage(attachment: AssistantImageAttachment) {
        update {
            copy(messages = messages.replaceLastUserMessageImage(attachment))
        }
    }

    private fun appendAssistantResult(result: AssistantChatResult, requestMessages: List<LlmChatMessage>) {
        val completion = result.completion
        val responseText = completion.content.trim()
        update {
            copy(
                messages = messages + AssistantMessage(
                    AssistantMessageRole.Assistant,
                    responseText.ifBlank {
                        "The assistant returned no final text (finish_reason=${completion.finishReason.ifBlank { "unknown" }}). The debug bar has the token details."
                    },
                ),
                isLoading = false,
                lastUsageLabel = result.usageLabel(),
                debugInfo = result.toDebugInfo(
                    requestMessages = requestMessages,
                    previous = debugInfo,
                ),
                error = null,
            )
        }
    }

    private fun appendAssistantError(message: String) {
        update {
            copy(
                messages = messages + AssistantMessage(AssistantMessageRole.System, "Error: $message"),
                isLoading = false,
                error = message,
            )
        }
    }
}

private const val MaxToolPlanningRounds = 3
private const val MaxToolCallsPerTurn = 5

private data class ToolLoopResult(
    val handled: Boolean,
    val success: Boolean,
    val reply: String,
    val errorMessage: String,
    val assistantResult: AssistantChatResult? = null,
)

private fun AssistantToolPlan.toToolRequest(): AssistantToolRequest =
    AssistantToolRequest(
        tool = tool,
        description = description,
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        steps = steps,
        cfgScale = cfgScale,
        seed = seed,
        sampler = sampler,
        replaceExistingComposition = replaceExistingComposition,
    )

private fun List<LlmChatMessage>.estimatedTokenCount(): Int {
    val chars = sumOf { it.role.length + it.content.length + 4 }
    return ceil(chars / 4.0).toInt().coerceAtLeast(size)
}

private fun estimatePromptTokens(messages: List<LlmChatMessage>): Int = messages.estimatedTokenCount()

private fun com.diffusiondesk.desktop.core.LlmPreset.effectiveArgsOrEmpty(): List<String> {
    return effectiveAdvancedArgs().getOrDefault(emptyList())
}

private fun AssistantChatResult.usageLabel(): String? {
    val total = completion.totalTokens ?: return null
    return "ctx $total"
}

private fun AssistantChatResult.withDisplayContent(content: String): AssistantChatResult =
    copy(completion = completion.copy(content = content))

private fun AssistantChatResult.toDebugInfo(
    requestMessages: List<LlmChatMessage>,
    previous: AssistantDebugInfo?,
): AssistantDebugInfo {
    return AssistantDebugInfo(
        estimatedPromptTokens = previous?.estimatedPromptTokens ?: estimatePromptTokens(requestMessages),
        contextLimitTokens = contextLimit.tokens,
        contextLimitSource = contextLimit.source,
        requestMessages = requestMessages.size,
        maxTokens = maxTokens,
        promptTokens = completion.promptTokens,
        completionTokens = completion.completionTokens,
        totalTokens = completion.totalTokens,
        promptTokensPerSecond = completion.promptTokensPerSecond,
        predictedTokensPerSecond = completion.predictedTokensPerSecond,
        elapsedMs = completion.elapsedMs,
        finishReason = completion.finishReason.ifBlank { null },
        contentChars = completion.content.length,
        reasoningChars = completion.reasoningContent.length,
        workerBaseUrl = baseUrl,
        presetName = presetName,
        advancedArgs = advancedArgs,
    )
}

internal fun buildAssistantAgentMessages(
    messages: List<AssistantMessage>,
    context: AssistantContextSnapshot,
): List<LlmChatMessage> {
    val history = messages
        .filter { it.role != AssistantMessageRole.System && it.role != AssistantMessageRole.Tool }
        .takeLast(12)
    val newestImageIndex = history.indexOfLast { it.imageAttachment != null }
    val mappedHistory = history
        .mapIndexed { index, message ->
            LlmChatMessage(
                role = when (message.role) {
                    AssistantMessageRole.User -> "user"
                    AssistantMessageRole.Assistant -> "assistant"
                    AssistantMessageRole.System -> "system"
                    AssistantMessageRole.Tool -> "system"
                },
                content = message.content,
                imageDataUri = message.imageAttachment?.dataUri?.takeIf { index == newestImageIndex },
            )
        }

    return listOf(
        LlmChatMessage(
            role = "system",
            content = buildString {
                appendLine("You are Diffusion Desk's Creative Assistant inside the Compose desktop app.")
                appendLine("Help the user refine prompts, understand image-generation settings, and plan Ideogram composition edits.")
                appendLine("You cannot start image generation. The user must press Generate.")
                appendLine("When suggesting composition changes, be concrete and refer to fields or selected elements.")
                appendLine("Reply in English by default.")
                appendLine()
                appendLine("Use the provided app tools directly when the user asks you to change prompt fields, parameters, or composition.")
                appendLine("Do not claim you cannot edit fields when a provided tool can do it.")
                appendLine("If a tool fails, use the error to choose a corrected next tool or explain what is needed.")
                appendLine("If the current prompt is empty and the user asks to create both a prompt and a composition, call set_prompt first with a complete image prompt, then call generate_structured_prompt.")
                appendLine("If a structured composition already exists, preserve it by default. For incremental changes, update the normal prompt with set_prompt if useful, then use improve_high_level, improve_style, improve_composition, improve_background, improve_selected_element, add_object, add_text, palette tools, or element tools. Do not call generate_structured_prompt unless the user is intentionally replacing the existing composition with a completely different image concept.")
                appendLine("If the user says \"apply prompt\", \"use that prompt\", \"go ahead\", \"do it\", or similar, infer the intended prompt from the conversation and call set_prompt.")
                appendLine()
                appendLine("Current app context:")
                appendLine("screen: ${context.screen}")
                appendLine("prompt_mode: ${context.promptMode}")
                appendLine("selected_preset: ${context.selectedPreset}")
                appendLine("prompt: ${context.prompt}")
                appendLine("negative_prompt: ${context.negativePrompt}")
                appendLine("width: ${context.width}")
                appendLine("height: ${context.height}")
                appendLine("target_canvas: ${context.width} x ${context.height} (${context.targetAspectLabel()})")
                appendLine("steps: ${context.steps}")
                appendLine("cfg_scale: ${context.cfgScale}")
                appendLine("sampler: ${context.sampler}")
                appendLine("seed: ${context.seed}")
                appendLine("selected_composition_element: ${context.selectedCompositionElement}")
                appendLine("composition_summary: ${context.compositionSummary}")
                val lastImage = messages.lastOrNull { it.imageAttachment != null }?.imageAttachment
                appendLine("attached_or_recent_reference_image: ${lastImage?.sizeLabel() ?: "none"}")
                appendLine("When creating or editing structured composition, preserve the target_canvas aspect ratio unless the user explicitly asks to change it.")
            },
        ),
    ) + mappedHistory
}

private fun AssistantContextSnapshot.targetAspectLabel(): String {
    val widthValue = width.toIntOrNull()
    val heightValue = height.toIntOrNull()
    if (widthValue == null || heightValue == null || widthValue <= 0 || heightValue <= 0) return "unknown aspect"
    val gcd = gcd(widthValue, heightValue)
    val ratio = widthValue.toDouble() / heightValue.toDouble()
    val orientation = when {
        ratio > 1.05 -> "landscape"
        ratio < 0.95 -> "portrait"
        else -> "square"
    }
    return "${widthValue / gcd}:${heightValue / gcd}, $orientation"
}

private fun AssistantImageAttachment.sizeLabel(): String {
    val size = if (width != null && height != null) {
        "$width x $height (${aspectLabel(width, height)})"
    } else {
        "unknown size"
    }
    return "$name, $size"
}

private fun aspectLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "unknown aspect"
    val gcd = gcd(width, height)
    val ratio = width.toDouble() / height.toDouble()
    val orientation = when {
        ratio > 1.05 -> "landscape"
        ratio < 0.95 -> "portrait"
        else -> "square"
    }
    return "${width / gcd}:${height / gcd}, $orientation"
}

private tailrec fun gcd(a: Int, b: Int): Int =
    if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)

private fun BufferedImage.toThumbnailDataUri(maxWidth: Int = 360, maxHeight: Int = 180): String {
    val scale = minOf(maxWidth.toDouble() / width, maxHeight.toDouble() / height, 1.0)
    val thumbnailWidth = (width * scale).toInt().coerceAtLeast(1)
    val thumbnailHeight = (height * scale).toInt().coerceAtLeast(1)
    val thumbnail = BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = thumbnail.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(this, 0, 0, thumbnailWidth, thumbnailHeight, null)
    } finally {
        graphics.dispose()
    }
    val output = ByteArrayOutputStream()
    ImageIO.write(thumbnail, "png", output)
    return "data:image/png;base64,${Base64.getEncoder().encodeToString(output.toByteArray())}"
}

private fun AssistantToolRequest.displayLabel(): String = buildString {
    append("Tool: ").append(tool)
    val args = listOfNotNull(
        description.takeIf(String::isNotBlank)?.let { "description=\"$it\"" },
        prompt.takeIf(String::isNotBlank)?.let { "prompt=\"$it\"" },
        negativePrompt.takeIf(String::isNotBlank)?.let { "negative_prompt=\"$it\"" },
        width?.let { "width=$it" },
        height?.let { "height=$it" },
        steps?.let { "steps=$it" },
        cfgScale?.let { "cfg=$it" },
        seed?.let { "seed=$it" },
        sampler.takeIf(String::isNotBlank)?.let { "sampler=$it" },
        "replace_existing=true".takeIf { replaceExistingComposition },
    )
    if (args.isNotEmpty()) {
        append("(").append(args.joinToString(", ")).append(")")
    }
}

private fun AssistantToolRequest.inputJson(): String {
    val fields = listOfNotNull(
        jsonField("description", description).takeIf { description.isNotBlank() },
        jsonField("prompt", prompt).takeIf { prompt.isNotBlank() },
        jsonField("negative_prompt", negativePrompt).takeIf { negativePrompt.isNotBlank() },
        width?.let { jsonField("width", it.toString(), quoted = false) },
        height?.let { jsonField("height", it.toString(), quoted = false) },
        steps?.let { jsonField("steps", it.toString(), quoted = false) },
        cfgScale?.let { jsonField("cfg_scale", it.toString(), quoted = false) },
        seed?.let { jsonField("seed", it.toString(), quoted = false) },
        jsonField("sampler", sampler).takeIf { sampler.isNotBlank() },
        jsonField("replace_existing", replaceExistingComposition.toString(), quoted = false).takeIf { replaceExistingComposition },
    )
    return "{\n  \"tool\": \"${tool.escapeJson()}\"" +
        fields.joinToString(separator = "", prefix = if (fields.isEmpty()) "" else ",") { "\n  $it" } +
        "\n}"
}

private fun AssistantToolResult.outputJson(): String {
    return "{\n  \"status\": \"${if (accepted) "success" else "error"}\",\n  \"message\": \"${message.escapeJson()}\"\n}"
}

private fun List<AssistantMessage>.replaceLastToolCall(
    name: String,
    input: String,
    output: String,
    success: Boolean,
): List<AssistantMessage> {
    val index = indexOfLast { it.role == AssistantMessageRole.Tool }
    if (index < 0) return this
    return mapIndexed { itemIndex, message ->
        if (itemIndex == index) {
            message.copy(
                toolCall = AssistantToolCall(
                    name = name,
                    input = input,
                    output = output,
                    success = success,
                ),
            )
        } else {
            message
        }
    }
}

private fun List<AssistantMessage>.replaceLastUserMessageImage(attachment: AssistantImageAttachment): List<AssistantMessage> {
    val index = indexOfLast { it.role == AssistantMessageRole.User }
    if (index < 0) return this
    return mapIndexed { itemIndex, message ->
        if (itemIndex == index) {
            message.copy(imageAttachment = attachment)
        } else {
            message
        }
    }
}

private fun jsonField(key: String, value: String, quoted: Boolean = true): String {
    val encoded = if (quoted) "\"${value.escapeJson()}\"" else value
    return "\"$key\": $encoded"
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
