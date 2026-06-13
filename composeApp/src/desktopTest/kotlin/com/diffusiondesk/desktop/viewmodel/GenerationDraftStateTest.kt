package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.ImagePromptMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertTrue(state.isCurrentDraftResolutionModified)
    }

    @Test
    fun textChangeMarksDraftModifiedImmediately() {
        val state = stateFor(params, ImagePromptMode.Text).copy(prompt = "next prompt")

        assertTrue(state.isCurrentDraftModified)
        assertFalse(state.isCurrentDraftResolutionModified)
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
        assertFalse(state.isCurrentDraftResolutionModified)
    }

    @Test
    fun generationSettingChangeKeepsCurrentResolution() {
        val state = stateFor(params, ImagePromptMode.Text).copy(
            steps = "8",
            cfgScale = "2.0",
            seed = "42",
            sampler = "euler",
        )

        assertTrue(state.isCurrentDraftModified)
        assertFalse(state.isCurrentDraftResolutionModified)
    }

    @Test
    fun compositionReferenceRequiresExplicitlyEnabledVisibleImage() {
        assertEquals("image", selectCompositionReferenceImage("image", enabled = true, resolutionModified = false))
        assertNull(selectCompositionReferenceImage("image", enabled = false, resolutionModified = false))
        assertNull(selectCompositionReferenceImage("image", enabled = true, resolutionModified = true))
        assertNull(selectCompositionReferenceImage<String>(null, enabled = true, resolutionModified = false))
    }

    @Test
    fun startNewCompositionClearsDraftButKeepsPromptHistory() {
        val state = GenerationUiState(
            prompt = "Current prompt",
            promptHistory = listOf("First prompt", "Current prompt"),
            promptHistoryIndex = 1,
            negativePrompt = "Current negative prompt",
            activeCompositionImproveAction = "improve_style",
            ideogram = IdeogramUiState(
                jsonPrompt = "{\"compositional_deconstruction\":{}}",
                jsonStatus = "Composition ready.",
                history = listOf("old composition"),
            ),
            selectedCompositionElementIndex = 2,
        )

        val reset = state.startNewCompositionDraft()

        assertEquals("", reset.prompt)
        assertEquals("", reset.negativePrompt)
        assertEquals(state.promptHistory, reset.promptHistory)
        assertEquals(state.promptHistoryIndex, reset.promptHistoryIndex)
        assertEquals("", reset.ideogram.jsonPrompt)
        assertEquals("No composition yet.", reset.ideogram.jsonStatus)
        assertEquals(IdeogramStructureTab.Preview, reset.ideogram.selectedTab)
        assertTrue(reset.ideogram.history.isEmpty())
        assertNull(reset.activeCompositionImproveAction)
        assertEquals(0, reset.selectedCompositionElementIndex)
    }

    @Test
    fun generationProgressUsesClipSamplingAndVaeRangesWithoutMovingBackward() {
        val clip = nextGenerationOverallProgress(0f, "prepare", step = 9, steps = 10)
        val firstSamplingStep = nextGenerationOverallProgress(clip, "sampling", step = 1, steps = 20)
        val finalSamplingStep = nextGenerationOverallProgress(firstSamplingStep, "sampling", step = 20, steps = 20)
        val vae = nextGenerationOverallProgress(finalSamplingStep, "decode", step = 1, steps = 2)

        assertEquals(0.045f, clip, 0.0001f)
        assertEquals(0.095f, firstSamplingStep, 0.0001f)
        assertEquals(0.95f, finalSamplingStep, 0.0001f)
        assertEquals(0.975f, vae, 0.0001f)
    }

    @Test
    fun generationProgressNeverDropsOnAStageCounterReset() {
        val previous = 0.72f

        assertEquals(
            previous,
            nextGenerationOverallProgress(previous, "sampling", step = 1, steps = 30),
            0.0001f,
        )
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
