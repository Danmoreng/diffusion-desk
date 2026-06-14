package com.diffusiondesk.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration

private const val AssistantDefaultMaxTokens = 4096
private const val AssistantContextGuardTokens = 512

data class LlmContextLimit(
    val tokens: Int?,
    val source: String,
)

data class AssistantChatResult(
    val completion: LlmChatCompletion,
    val presetName: String,
    val baseUrl: String,
    val advancedArgs: List<String>,
    val contextLimit: LlmContextLimit,
    val maxTokens: Int,
)

data class AssistantToolPlan(
    val tool: String,
    val description: String = "",
    val reply: String = "",
    val prompt: String = "",
    val negativePrompt: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val steps: Int? = null,
    val cfgScale: Double? = null,
    val seed: Int? = null,
    val sampler: String = "",
)

data class AssistantToolBatch(
    val calls: List<AssistantToolPlan>,
    val reply: String = "",
)

class LlmRoleService(
    private val llmWorkerPool: LlmWorkerPool,
    private val client: DiffusionDeskClient,
) {
    suspend fun chatWithAssistant(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        messages: List<LlmChatMessage>,
    ): Result<String> = withContext(Dispatchers.IO) {
        chatWithAssistantDetailed(settings, presets, roles, messages).mapCatching { result ->
            result.completion.content.ifBlank {
                error("Assistant returned empty content (finish_reason=${result.completion.finishReason}).")
            }
        }
    }

    suspend fun chatWithAssistantDetailed(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        messages: List<LlmChatMessage>,
    ): Result<AssistantChatResult> = withContext(Dispatchers.IO) {
        runCatching {
            val preset = presets.firstOrNull { it.id == roles.assistantPresetId }
                ?: error("Select an assistant LLM preset first.")
            val advancedArgs = preset.effectiveAdvancedArgs().getOrThrow()
            val contextLimit = inferLlmContextLimit(advancedArgs)
            val maxTokens = assistantMaxTokens(messages, contextLimit.tokens)
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            val completion = client.chatCompletionDetailed(
                baseUrl = worker.baseUrl,
                model = preset.modelPath,
                messages = messages,
                maxTokens = maxTokens,
                temperature = 0.2,
                enableThinking = false,
                timeout = Duration.ofMinutes(5),
            ).getOrThrow()
            AssistantChatResult(
                completion = completion,
                presetName = preset.name,
                baseUrl = worker.baseUrl,
                advancedArgs = advancedArgs,
                contextLimit = contextLimit,
                maxTokens = maxTokens,
            )
        }
    }

    suspend fun planAssistantTool(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        userMessage: String,
        appContext: String,
    ): Result<AssistantToolPlan> = planAssistantTools(settings, presets, roles, userMessage, appContext)
        .mapCatching { it.calls.firstOrNull() ?: AssistantToolPlan("none") }

    suspend fun planAssistantTools(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        userMessage: String,
        appContext: String,
        toolFeedback: List<String> = emptyList(),
    ): Result<AssistantToolBatch> = withContext(Dispatchers.IO) {
        runCatching {
            val preset = presets.firstOrNull { it.id == roles.assistantPresetId }
                ?: error("Select an assistant LLM preset first.")
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            val response = client.chatCompletion(
                baseUrl = worker.baseUrl,
                model = preset.modelPath,
                messages = listOf(
                    LlmChatMessage(
                        role = "system",
                        content = ASSISTANT_TOOL_PLANNER_PROMPT,
                    ),
                    LlmChatMessage(
                        role = "user",
                        content = buildString {
                            appendLine("User message: $userMessage")
                            appendLine()
                            appendLine("Current app context:")
                            append(appContext)
                            if (toolFeedback.isNotEmpty()) {
                                appendLine()
                                appendLine("Previous tool results/errors:")
                                toolFeedback.forEach { appendLine("- $it") }
                            }
                        },
                    ),
                ),
                maxTokens = 1024,
                temperature = 0.0,
                jsonResponse = true,
                enableThinking = false,
                timeout = Duration.ofMinutes(2),
            ).getOrThrow()
            parseAssistantToolBatch(response)
        }
    }

    suspend fun enhancePrompt(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        prompt: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val presetId = roles.promptEnhancerPresetId.ifBlank { roles.assistantPresetId }
            val preset = presets.firstOrNull { it.id == presetId }
                ?: error("Select a prompt enhancer or assistant LLM preset first.")
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            client.chatCompletion(
                baseUrl = worker.baseUrl,
                model = preset.modelPath,
                messages = listOf(
                    LlmChatMessage(
                        role = "system",
                        content = "Rewrite the user's image prompt to be clearer, more specific, and more useful for image generation. Return only the improved prompt.",
                    ),
                    LlmChatMessage(role = "user", content = prompt),
                ),
                maxTokens = 1024,
                temperature = 0.0,
                enableThinking = false,
            ).getOrThrow()
        }
    }

    suspend fun completeIdeogramCompositionAction(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        systemPrompt: String,
        userPrompt: String,
        imageDataUri: String?,
        maxTokens: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val presetId = roles.promptEnhancerPresetId.ifBlank { roles.assistantPresetId }
            val preset = presets.firstOrNull { it.id == presetId }
                ?: error("Select a prompt enhancer or assistant LLM preset first.")
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            client.compositionChatCompletion(
                baseUrl = worker.baseUrl,
                model = preset.modelPath,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imageDataUri = imageDataUri?.takeIf { preset.mmprojPath.isNotBlank() },
                maxTokens = maxTokens,
            ).getOrThrow()
        }
    }

    suspend fun completeIdeogramGenerationStage(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        messages: List<LlmChatMessage>,
        maxTokens: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val presetId = roles.promptEnhancerPresetId.ifBlank { roles.assistantPresetId }
            val preset = presets.firstOrNull { it.id == presetId }
                ?: error("Select a prompt enhancer or assistant LLM preset first.")
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            client.chatCompletion(
                baseUrl = worker.baseUrl,
                model = preset.modelPath,
                messages = messages,
                maxTokens = maxTokens,
                temperature = 0.0,
                jsonResponse = true,
                reasoningFormat = "deepseek",
                enableThinking = true,
                reasoningBudgetTokens = 256,
                cachePrompt = true,
                slotId = 0,
                timeout = Duration.ofMinutes(10),
            ).getOrThrow()
        }
    }

}

internal fun inferLlmContextLimit(args: List<String>): LlmContextLimit {
    val options = setOf("--ctx-size", "-c", "--fit-ctx", "-fitc")
    args.forEachIndexed { index, token ->
        val option = token.substringBefore("=")
        if (option in options) {
            val rawValue = token.substringAfter("=", missingDelimiterValue = "")
                .ifBlank { args.getOrNull(index + 1).orEmpty() }
            val tokens = parseTokenCount(rawValue)
            val source = if (option in setOf("--fit-ctx", "-fitc")) "fit-ctx" else "ctx-size"
            return LlmContextLimit(tokens, "$source=$rawValue")
        }
    }
    return LlmContextLimit(null, "not set")
}

internal fun parseTokenCount(value: String): Int? {
    val trimmed = value.trim().lowercase()
    if (trimmed.isBlank()) return null
    val match = Regex("""^(\d+(?:\.\d+)?)([kmg]?)$""").matchEntire(trimmed) ?: return null
    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2]) {
        "k" -> 1024.0
        "m" -> 1024.0 * 1024.0
        "g" -> 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (number * multiplier).toInt()
}

private fun assistantMaxTokens(messages: List<LlmChatMessage>, contextLimit: Int?): Int {
    val estimatedPromptTokens = messages.sumOf { message ->
        ((message.role.length + message.content.length + 4) + 3) / 4
    }.coerceAtLeast(messages.size)
    val available = contextLimit
        ?.let { it - estimatedPromptTokens - AssistantContextGuardTokens }
        ?: AssistantDefaultMaxTokens
    return available.coerceIn(512, AssistantDefaultMaxTokens)
}

private fun parseAssistantToolBatch(response: String): AssistantToolBatch {
    val root = assistantToolJson.parseToJsonElement(response).jsonObject
    val calls = root["tool_calls"]?.jsonArray
        ?.mapNotNull { element -> parseAssistantToolPlan(element.jsonObject).takeUnless { it.tool == "none" } }
        ?: listOf(parseAssistantToolPlan(root)).filter { it.tool != "none" }
    return AssistantToolBatch(calls = calls, reply = root.string("reply"))
}

private fun parseAssistantToolPlan(root: JsonObject): AssistantToolPlan {
    val tool = root.string("tool").ifBlank { "none" }
    val description = root.string("description")
    val reply = root.string("reply")
    return AssistantToolPlan(
        tool = tool,
        description = description,
        reply = reply,
        prompt = root.string("prompt"),
        negativePrompt = root.string("negative_prompt"),
        width = root.int("width"),
        height = root.int("height"),
        steps = root.int("steps"),
        cfgScale = root.double("cfg_scale"),
        seed = root.int("seed"),
        sampler = root.string("sampler"),
    )
}

private fun JsonObject.string(key: String): String =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.int(key: String): Int? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

private fun JsonObject.double(key: String): Double? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.toDoubleOrNull()

private val assistantToolJson = Json { ignoreUnknownKeys = true }

private const val ASSISTANT_TOOL_PLANNER_PROMPT = """
You route the user's message to one existing Diffusion Desk UI action when appropriate.
Return only JSON with keys: tool_calls, reply.
tool_calls must be an array of objects with keys: tool, description, prompt, negative_prompt, width, height, steps, cfg_scale, seed, sampler.
Use null for numeric keys that do not apply and "" for unused strings.

Use an empty tool_calls array when the user is asking a question, wants explanation, or no existing action fits.
You may return multiple tool calls when the user asks for multiple changes. Order matters.
If a previous tool failed, use the error to choose a corrected next tool or return no tool calls with a short reply explaining what is needed.
If a composition tool fails because no valid structured composition exists, call generate_structured_prompt next.
Use description only for add_object or add_text. Keep it concise and include the user's requested visual content.
Use prompt only for set_prompt. Use negative_prompt only for set_negative_prompt.
Use reply as one short user-facing sentence in English.

Available tools:
- set_prompt: replace the normal Prompt field. Requires prompt.
- enhance_prompt: improve the current normal Prompt field using the app's prompt enhancer.
- set_negative_prompt: replace the Negative Prompt field. Requires negative_prompt.
- clear_negative_prompt: clear the Negative Prompt field.
- generate_structured_prompt: create a full structured Ideogram prompt from the current normal prompt.
- improve_high_level: improve the high-level description field.
- improve_style: improve the style fields.
- improve_composition: improve background and element placements.
- improve_background: improve only the background field.
- improve_selected_element: improve the currently selected element description.
- suggest_global_palette: suggest the global style palette.
- suggest_selected_element_palette: suggest palette for the selected element.
- regenerate_selected_element: create a variant of the selected element.
- add_object: add one object element. Requires description.
- add_text: add one text element. Requires description including the literal text if requested.
- delete_selected_element: delete the currently selected element.
- set_image_size: set exact width and height. Requires width and height.
- set_portrait: make the current image size portrait/tall.
- set_landscape: make the current image size landscape/wide.
- set_square: make the current image size square.
- set_steps: set sampling steps. Requires steps.
- set_cfg_scale: set CFG scale. Requires cfg_scale.
- set_seed: set seed. Requires seed.
- randomize_seed: set seed to random.
- set_sampler: set sampler. Requires sampler.

Examples:
{"tool_calls":[{"tool":"set_prompt","description":"","prompt":"minimalist vector art wallpaper, dark mode aesthetic, glowing cyan lines","negative_prompt":"","width":null,"height":null,"steps":null,"cfg_scale":null,"seed":null,"sampler":""}],"reply":"I updated the prompt."}
{"tool_calls":[{"tool":"add_object","description":"a small ceramic cup beside the strawberries","prompt":"","negative_prompt":"","width":null,"height":null,"steps":null,"cfg_scale":null,"seed":null,"sampler":""},{"tool":"set_landscape","description":"","prompt":"","negative_prompt":"","width":null,"height":null,"steps":null,"cfg_scale":null,"seed":null,"sampler":""}],"reply":"I added the object and switched to landscape."}
{"tool_calls":[{"tool":"set_image_size","description":"","prompt":"","negative_prompt":"","width":1024,"height":1536,"steps":null,"cfg_scale":null,"seed":null,"sampler":""}],"reply":"I set the image size to 1024 x 1536."}
{"tool_calls":[],"reply":""}
"""
