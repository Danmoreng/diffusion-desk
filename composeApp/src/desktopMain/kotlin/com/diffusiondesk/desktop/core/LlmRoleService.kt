package com.diffusiondesk.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                        content = "Canvas: ${width}x${height}\nRaw prompt: $prompt",
                    ),
                ),
            ).getOrThrow()
        }
    }

    private companion object {
        private const val IDEOGRAM_JSON_SYSTEM_PROMPT = """
You convert raw image prompts into Ideogram 4 structured JSON captions. Return only valid JSON, with no markdown fences or commentary.

Use exactly these top-level keys in this order:
1. high_level_description
2. style_description
3. compositional_deconstruction

style_description must contain aesthetics, lighting, exactly one of photo or art_style, medium, and optionally color_palette. For photographs, order keys as aesthetics, lighting, photo, medium, color_palette. For non-photographic images, order keys as aesthetics, lighting, medium, art_style, color_palette.

compositional_deconstruction must contain background then elements. elements is an array of objects. Object elements use keys type, bbox, desc, color_palette. Text elements use keys type, bbox, text, desc, color_palette. Use type "obj" or "text". bbox is optional and must be [y_min, x_min, y_max, x_max] in normalized 0-1000 coordinates.

Use uppercase #RRGGBB color strings. Use no more than 16 global colors and no more than 5 per element. If the user asks for readable text in the image, create text elements with the literal text in the text field.
"""
    }
}
