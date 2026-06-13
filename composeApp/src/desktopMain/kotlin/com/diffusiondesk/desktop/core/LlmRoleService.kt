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
