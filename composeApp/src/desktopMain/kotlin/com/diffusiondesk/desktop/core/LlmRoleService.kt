package com.diffusiondesk.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        runCatching {
            val preset = presets.firstOrNull { it.id == roles.assistantPresetId }
                ?: error("Select an assistant LLM preset first.")
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            client.chatCompletion(worker.baseUrl, preset.modelPath, messages).getOrThrow()
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

    suspend fun generateIdeogramJsonPrompt(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        prompt: String,
        width: Int,
        height: Int,
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
                        content = IDEOGRAM_JSON_SYSTEM_PROMPT,
                    ),
                    LlmChatMessage(
                        role = "user",
                        content = "Target canvas: ${width}x${height} (aspect ratio $width:$height).\nUser idea: $prompt",
                    ),
                ),
                maxTokens = 4096,
                temperature = 0.0,
                jsonResponse = true,
                reasoningFormat = "deepseek",
                enableThinking = true,
                reasoningBudgetTokens = 512,
                timeout = Duration.ofMinutes(10),
            ).getOrThrow()
        }
    }

    suspend fun improveIdeogramField(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        targetPath: String,
        currentValue: String,
        documentJson: String,
        width: Int,
        height: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val presetId = roles.promptEnhancerPresetId.ifBlank { roles.assistantPresetId }
            val preset = presets.firstOrNull { it.id == presetId }
                ?: error("Select a prompt enhancer or assistant LLM preset first.")
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            val response = client.chatCompletion(
                baseUrl = worker.baseUrl,
                model = preset.modelPath,
                messages = listOf(
                    LlmChatMessage(role = "system", content = IDEOGRAM_FIELD_IMPROVEMENT_SYSTEM_PROMPT),
                    LlmChatMessage(
                        role = "user",
                        content = "Target path: $targetPath\nCanvas: ${width}x${height}\nCurrent value: $currentValue\nFull composition context: $documentJson",
                    ),
                ),
                maxTokens = 1024,
                temperature = 0.2,
                jsonResponse = true,
                enableThinking = false,
                timeout = Duration.ofMinutes(5),
            ).getOrThrow()
            val value = parseIdeogramFieldImprovement(response)
            require(value.isNotBlank()) { "The LLM returned an empty field value." }
            value
        }
    }

    private companion object {
        private const val IDEOGRAM_JSON_SYSTEM_PROMPT = """
You convert raw image prompts into Ideogram 4 structured JSON captions. Think briefly, decide the visual layout once, then return only valid JSON. Do not write markdown or commentary.

Emit exactly one JSON object using these top-level keys in order: high_level_description, style_description, compositional_deconstruction. Preserve literal non-ASCII characters.

style_description requires aesthetics, lighting, medium, and exactly one of photo or art_style. For photographs, order keys as aesthetics, lighting, photo, medium, then optional color_palette. For non-photographic work, order keys as aesthetics, lighting, medium, art_style, then optional color_palette.

compositional_deconstruction keys are background, then elements. background is a string describing the scene shell: architecture, ground or floor, sky, atmosphere, distant scenery, and scene-wide lighting. Do not duplicate those components as objects. Individually placeable people, animals, vehicles, furniture, products, signs, and props belong in elements.

Elements use type "obj" or "text". Object key order: type, optional bbox, desc, optional color_palette. Text key order: type, optional bbox, text, desc, optional color_palette. bbox is [y_min, x_min, y_max, x_max] in normalized 0-1000 coordinates and must account for the target aspect ratio. Include a bbox where precise placement matters; omit it for dense or hard-to-enumerate visuals.

Create one obj for each coherent subject rather than splitting a subject into anatomical or structural parts. Preserve every named visual unit from the user idea. Every distinct readable text block gets one text element with exact literal characters. Anonymous crowds and distant scenery may remain in background.

Descriptions must be concrete, visual, and specific. Avoid analysis, alternatives, hedging, shadows, and repeated scene context inside element descriptions. Use uppercase #RRGGBB colors, at most 16 global colors and at most 5 per element. Return compact JSON only.
"""
        private const val IDEOGRAM_FIELD_IMPROVEMENT_SYSTEM_PROMPT = """
You improve exactly one field in an Ideogram 4 structured caption. Return only a JSON object with one string property: {"value":"..."}.

Use the full composition only as context. Do not return a full caption, patches, explanations, markdown, or additional keys. Preserve the field's purpose and improve visual specificity, clarity, consistency, and useful detail. Do not introduce alternatives or hedging. Keep literal in-image text unchanged because text content is edited separately.
"""
    }
}

internal fun parseIdeogramFieldImprovement(response: String): String {
    val root = Json.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("value")) { "The LLM field patch must contain only the value property." }
    return root["value"]?.jsonPrimitive?.content?.trim().orEmpty()
}
