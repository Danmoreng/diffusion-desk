package com.diffusiondesk.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

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
                        content = "Canvas: ${width}x${height}\nRaw prompt: $prompt",
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

    private companion object {
        private const val IDEOGRAM_JSON_SYSTEM_PROMPT = """
You convert raw image prompts into Ideogram 4 structured JSON captions. Think briefly, decide the visual layout once, then return only valid JSON. Do not write markdown or commentary.

Required top-level keys: high_level_description, style_description, compositional_deconstruction.

style_description requires aesthetics, lighting, medium, and exactly one of photo or art_style. If the result is photographic, include photo and omit art_style. color_palette is optional.

compositional_deconstruction requires background and elements. background must be a single string, not an object. Elements use type "obj" or "text". obj keys: type, bbox, desc, optional color_palette. text keys: type, bbox, text, desc, optional color_palette. bbox is [y_min, x_min, y_max, x_max] in 0-1000 coordinates on a 10-unit grid.

Keep the JSON compact. Use 3-8 important elements for most prompts, maximum 12. Create one obj for each named foreground character or distinct foreground subject. Do not collapse named lineups into one group element. Use group elements only for anonymous crowds, scenery, or low-importance background items. Omit minor props unless they are central to the prompt.

Descriptions should be concise, visual, and specific. Avoid analysis, alternatives, and long explanations. Use uppercase #RRGGBB colors, at most 16 global colors and at most 5 per element. If readable text is requested, create a text element with the exact literal text.
"""
    }
}
