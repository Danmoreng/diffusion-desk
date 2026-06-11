package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.ImagePromptMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationDraftStateTest {
    private val params = GenerationParams(
        prompt = "prompt",
        negativePrompt = "negative",
        width = 1024,
        height = 768,
        steps = 4,
        cfgScale = 1.0,
        seed = -1,
        sampler = "euler_a",
    )

    @Test
    fun matchingTextDraftKeepsGeneratedImageActive() {
        val state = stateFor(params, ImagePromptMode.Text).copy(
            prompt = "prompt",
            negativePrompt = "negative",
        )

        assertFalse(state.isCurrentDraftModified)
    }

    @Test
    fun dimensionChangeMarksDraftModifiedImmediately() {
        val state = stateFor(params, ImagePromptMode.Text).copy(width = "768")

        assertTrue(state.isCurrentDraftModified)
    }

    @Test
    fun textChangeMarksDraftModifiedImmediately() {
        val state = stateFor(params, ImagePromptMode.Text).copy(prompt = "next prompt")

        assertTrue(state.isCurrentDraftModified)
    }

    @Test
    fun jsonWhitespaceDoesNotMarkDraftModified() {
        val pretty = """
            {
              "high_level_description": "Scene",
              "compositional_deconstruction": {
                "background": "Studio",
                "elements": [{"type": "obj", "desc": "Subject"}]
              }
            }
        """.trimIndent()
        val compact = parseIdeogramCompositionDocument(pretty).getOrThrow().serializeForBackend()
        val jsonParams = params.copy(prompt = compact, negativePrompt = "")
        val state = stateFor(jsonParams, ImagePromptMode.Json).copy(
            ideogram = IdeogramUiState(jsonPrompt = pretty),
        )

        assertFalse(state.isCurrentDraftModified)
    }

    @Test
    fun semanticJsonChangeMarksDraftModified() {
        val original = """{"compositional_deconstruction":{"background":"Studio","elements":[{"type":"obj","desc":"Subject"}]}}"""
        val changed = """{"compositional_deconstruction":{"background":"Outside","elements":[{"type":"obj","desc":"Subject"}]}}"""
        val jsonParams = params.copy(prompt = original, negativePrompt = "")
        val state = stateFor(jsonParams, ImagePromptMode.Json).copy(
            ideogram = IdeogramUiState(jsonPrompt = changed),
        )

        assertTrue(state.isCurrentDraftModified)
    }

    private fun stateFor(params: GenerationParams, mode: ImagePromptMode): GenerationUiState = GenerationUiState(
        prompt = params.prompt,
        negativePrompt = params.negativePrompt,
        width = params.width.toString(),
        height = params.height.toString(),
        steps = params.steps.toString(),
        cfgScale = params.cfgScale.toString(),
        seed = params.seed.toString(),
        sampler = params.sampler,
        history = listOf(
            GenerationHistoryItem(
                id = "history",
                status = GenerationStatus.Completed,
                params = params,
                promptMode = mode,
            ),
        ),
        historyIndex = 0,
    )
}
