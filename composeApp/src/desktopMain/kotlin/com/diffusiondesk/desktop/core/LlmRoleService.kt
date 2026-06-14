package com.diffusiondesk.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    val replaceExistingComposition: Boolean = false,
)

data class AssistantToolBatch(
    val calls: List<AssistantToolPlan>,
    val reply: String = "",
)

data class AssistantAgentTurn(
    val result: AssistantChatResult,
    val batch: AssistantToolBatch,
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
            require(messages.none { !it.imageDataUri.isNullOrBlank() } || preset.mmprojPath.isNotBlank()) {
                "The selected assistant LLM preset does not support images. Choose a vision-capable preset with an mmproj file."
            }
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

    suspend fun chatWithAssistantAgentDetailed(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        messages: List<LlmChatMessage>,
    ): Result<AssistantAgentTurn> = withContext(Dispatchers.IO) {
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
                tools = assistantToolDefinitions(),
                parallelToolCalls = true,
                timeout = Duration.ofMinutes(5),
            ).getOrThrow()
            val result = AssistantChatResult(
                completion = completion,
                presetName = preset.name,
                baseUrl = worker.baseUrl,
                advancedArgs = advancedArgs,
                contextLimit = contextLimit,
                maxTokens = maxTokens,
            )
            val batch = AssistantToolBatch(
                calls = completion.toolCalls.map { it.toAssistantToolPlan() },
                reply = completion.content,
            )
            AssistantAgentTurn(result = result, batch = batch)
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

private fun LlmToolCall.toAssistantToolPlan(): AssistantToolPlan {
    val root = runCatching { assistantToolJson.parseToJsonElement(arguments).jsonObject }
        .getOrDefault(JsonObject(emptyMap()))
    return AssistantToolPlan(
        tool = name,
        description = root.string("description"),
        prompt = root.string("prompt"),
        negativePrompt = root.string("negative_prompt"),
        width = root.int("width"),
        height = root.int("height"),
        steps = root.int("steps"),
        cfgScale = root.double("cfg_scale"),
        seed = root.int("seed"),
        sampler = root.string("sampler"),
        replaceExistingComposition = root.boolean("replace_existing"),
    )
}

private fun assistantToolDefinitions(): JsonArray = JsonArray(
    listOf(
        functionTool(
            name = "set_prompt",
            description = "Replace the normal Prompt field.",
            properties = mapOf("prompt" to stringSchema("The complete image prompt to put into the Prompt field.")),
            required = listOf("prompt"),
        ),
        functionTool("enhance_prompt", "Improve the current normal Prompt field using the app's prompt enhancer."),
        functionTool(
            name = "set_negative_prompt",
            description = "Replace the Negative Prompt field.",
            properties = mapOf("negative_prompt" to stringSchema("The negative prompt text.")),
            required = listOf("negative_prompt"),
        ),
        functionTool("clear_negative_prompt", "Clear the Negative Prompt field."),
        functionTool("inspect_latest_image", "Inspect the latest generated image in the assistant chat."),
        functionTool(
            name = "generate_structured_prompt",
            description = "Create a full structured Ideogram prompt from the current normal prompt. Use only when no structured composition exists yet, or when the user is clearly switching to a completely different image concept. Do not use this for incremental edits to an existing composition; use improve_* or element tools instead.",
            properties = mapOf("replace_existing" to booleanSchema("Set true only when the user is intentionally replacing an existing structured composition with a completely different image concept.")),
        ),
        functionTool("improve_high_level", "Improve the high-level description field."),
        functionTool("improve_style", "Improve the style fields."),
        functionTool("improve_composition", "Improve background and element placements."),
        functionTool("improve_background", "Improve only the background field."),
        functionTool("improve_selected_element", "Improve the currently selected element description."),
        functionTool("suggest_global_palette", "Suggest the global style palette."),
        functionTool("suggest_selected_element_palette", "Suggest palette for the selected element."),
        functionTool("regenerate_selected_element", "Create a variant of the selected element."),
        functionTool(
            name = "add_object",
            description = "Add one object element to the structured composition.",
            properties = mapOf("description" to stringSchema("The object to add.")),
            required = listOf("description"),
        ),
        functionTool(
            name = "add_text",
            description = "Add one text element to the structured composition.",
            properties = mapOf("description" to stringSchema("The text element to add, including literal text.")),
            required = listOf("description"),
        ),
        functionTool("delete_selected_element", "Delete the currently selected element."),
        functionTool(
            name = "set_image_size",
            description = "Set exact image width and height.",
            properties = mapOf(
                "width" to integerSchema("Image width in pixels."),
                "height" to integerSchema("Image height in pixels."),
            ),
            required = listOf("width", "height"),
        ),
        functionTool("set_portrait", "Make the current image size portrait/tall."),
        functionTool("set_landscape", "Make the current image size landscape/wide."),
        functionTool("set_square", "Make the current image size square."),
        functionTool(
            name = "set_steps",
            description = "Set sampling steps.",
            properties = mapOf("steps" to integerSchema("Sampling steps.")),
            required = listOf("steps"),
        ),
        functionTool(
            name = "set_cfg_scale",
            description = "Set CFG scale.",
            properties = mapOf("cfg_scale" to numberSchema("CFG scale.")),
            required = listOf("cfg_scale"),
        ),
        functionTool(
            name = "set_seed",
            description = "Set seed.",
            properties = mapOf("seed" to integerSchema("Seed value.")),
            required = listOf("seed"),
        ),
        functionTool("randomize_seed", "Set seed to random."),
        functionTool(
            name = "set_sampler",
            description = "Set sampler.",
            properties = mapOf("sampler" to stringSchema("Sampler name.")),
            required = listOf("sampler"),
        ),
    ),
)

private fun functionTool(
    name: String,
    description: String,
    properties: Map<String, JsonObject> = emptyMap(),
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put(
        "function",
        buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put(
                "parameters",
                buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", JsonObject(properties))
                    put("required", JsonArray(required.map { JsonPrimitive(it) }))
                },
            )
        },
    )
}

private fun stringSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
}

private fun integerSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("integer"))
    put("description", JsonPrimitive(description))
}

private fun numberSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("number"))
    put("description", JsonPrimitive(description))
}

private fun booleanSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("boolean"))
    put("description", JsonPrimitive(description))
}

private fun JsonObject.string(key: String): String =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

private fun JsonObject.int(key: String): Int? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

private fun JsonObject.double(key: String): Double? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.toDoubleOrNull()

private fun JsonObject.boolean(key: String): Boolean =
    (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.toBooleanStrictOrNull() ?: false

private val assistantToolJson = Json { ignoreUnknownKeys = true }
