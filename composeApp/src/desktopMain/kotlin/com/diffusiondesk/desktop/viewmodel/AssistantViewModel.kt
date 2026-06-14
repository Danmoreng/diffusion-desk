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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import java.time.LocalDateTime

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
)

data class AssistantToolCall(
    val name: String,
    val input: String,
    val output: String,
    val success: Boolean,
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
            val userMessage = AssistantMessage(AssistantMessageRole.User, trimmed)
            update {
                copy(
                    messages = messages + userMessage,
                    isLoading = true,
                    error = null,
                )
            }

            val requestMessages = buildAssistantMessages(_uiState.value.messages, context)
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

            val toolRun = runAssistantToolLoop(
                settings = settings,
                presets = presets,
                roles = roles,
                userMessage = trimmed,
                appContext = context.toPlannerContext(),
            )
            if (toolRun.handled) {
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

            llmRoleService.chatWithAssistantDetailed(
                settings = settings,
                presets = presets,
                roles = roles,
                messages = requestMessages,
            ).onSuccess { result ->
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
            }.onFailure { error ->
                val message = error.message ?: "Assistant request failed."
                update {
                    copy(
                        messages = messages + AssistantMessage(AssistantMessageRole.System, "Error: $message"),
                        isLoading = false,
                        error = message,
                    )
                }
            }
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

    private fun update(transform: AssistantUiState.() -> AssistantUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private suspend fun runAssistantToolLoop(
        settings: com.diffusiondesk.desktop.core.DesktopSettings,
        presets: List<com.diffusiondesk.desktop.core.LlmPreset>,
        roles: com.diffusiondesk.desktop.core.LlmRoleSettings,
        userMessage: String,
        appContext: String,
    ): ToolLoopResult {
        val feedback = mutableListOf<String>()
        var handled = false
        var lastReply = ""
        var lastError = ""
        var allSucceeded = true
        var executedCalls = 0

        repeat(MaxToolPlanningRounds) {
            val batch = llmRoleService.planAssistantTools(
                settings = settings,
                presets = presets,
                roles = roles,
                userMessage = userMessage,
                appContext = appContext,
                toolFeedback = feedback,
            ).getOrNull() ?: return@repeat
            val calls = batch.calls.take(MaxToolCallsPerTurn - executedCalls)
            if (calls.isEmpty()) {
                if (handled) {
                    lastReply = batch.reply.ifBlank { lastReply }
                }
                return ToolLoopResult(
                    handled = handled,
                    success = handled && allSucceeded,
                    reply = batch.reply.ifBlank { lastReply },
                    errorMessage = lastError,
                )
            }

            handled = true
            lastReply = batch.reply.ifBlank { lastReply }
            for (plan in calls) {
                val toolRequest = plan.toToolRequest()
                appendPendingToolCall(toolRequest)
                val toolResult = runTool(toolRequest)
                updateLastToolCall(toolRequest, toolResult)
                feedback += "${toolRequest.tool} ${if (toolResult.accepted) "succeeded" else "failed"}: ${toolResult.message}"
                executedCalls++
                if (!toolResult.accepted) {
                    allSucceeded = false
                    lastError = toolResult.message
                    return@repeat
                }
                if (executedCalls >= MaxToolCallsPerTurn) {
                    return ToolLoopResult(
                        handled = true,
                        success = true,
                        reply = lastReply,
                        errorMessage = "",
                    )
                }
            }
            allSucceeded = true
            lastError = ""
            return ToolLoopResult(
                handled = true,
                success = true,
                reply = lastReply,
                errorMessage = "",
            )
        }

        return ToolLoopResult(
            handled = handled,
            success = handled && allSucceeded && lastError.isBlank(),
            reply = lastReply,
            errorMessage = lastError,
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
}

private const val MaxToolPlanningRounds = 3
private const val MaxToolCallsPerTurn = 5

private data class ToolLoopResult(
    val handled: Boolean,
    val success: Boolean,
    val reply: String,
    val errorMessage: String,
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

internal fun buildAssistantMessages(
    messages: List<AssistantMessage>,
    context: AssistantContextSnapshot,
): List<LlmChatMessage> {
    val history = messages
        .filter { it.role != AssistantMessageRole.System && it.role != AssistantMessageRole.Tool }
        .takeLast(12)
        .map { message ->
            LlmChatMessage(
                role = when (message.role) {
                    AssistantMessageRole.User -> "user"
                    AssistantMessageRole.Assistant -> "assistant"
                    AssistantMessageRole.System -> "system"
                    AssistantMessageRole.Tool -> "system"
                },
                content = message.content,
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
                appendLine("Some structured prompt and composition actions may be executed by app tools before this chat response. If that happens, acknowledge the started action briefly instead of writing full JSON.")
                appendLine("Reply in English by default.")
                appendLine()
                appendLine("Current app context:")
                appendLine("screen: ${context.screen}")
                appendLine("prompt_mode: ${context.promptMode}")
                appendLine("selected_preset: ${context.selectedPreset}")
                appendLine("prompt: ${context.prompt}")
                appendLine("negative_prompt: ${context.negativePrompt}")
                appendLine("width: ${context.width}")
                appendLine("height: ${context.height}")
                appendLine("steps: ${context.steps}")
                appendLine("cfg_scale: ${context.cfgScale}")
                appendLine("sampler: ${context.sampler}")
                appendLine("seed: ${context.seed}")
                appendLine("selected_composition_element: ${context.selectedCompositionElement}")
                appendLine("composition_summary: ${context.compositionSummary}")
            },
        ),
    ) + history
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

private fun jsonField(key: String, value: String, quoted: Boolean = true): String {
    val encoded = if (quoted) "\"${value.escapeJson()}\"" else value
    return "\"$key\": $encoded"
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

private fun AssistantContextSnapshot.toPlannerContext(): String = buildString {
    appendLine("screen: $screen")
    appendLine("prompt_mode: $promptMode")
    appendLine("prompt: $prompt")
    appendLine("selected_preset: $selectedPreset")
    appendLine("selected_composition_element: $selectedCompositionElement")
    appendLine("composition_summary: $compositionSummary")
}
