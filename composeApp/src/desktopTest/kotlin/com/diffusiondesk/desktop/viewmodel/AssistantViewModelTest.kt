package com.diffusiondesk.desktop.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistantViewModelTest {
    @Test
    fun assistantMessagesIncludeContextAndRecentHistory() {
        val messages = listOf(
            AssistantMessage(AssistantMessageRole.Assistant, "Welcome"),
            AssistantMessage(AssistantMessageRole.User, "Make this sharper"),
            AssistantMessage(AssistantMessageRole.Assistant, "Try stronger material detail."),
        )

        val chat = buildAssistantMessages(
            messages = messages,
            context = AssistantContextSnapshot(
                screen = "Generate",
                promptMode = "JSON",
                prompt = "{\"compositional_deconstruction\":{}}",
                negativePrompt = "blur",
                width = "1024",
                height = "768",
                steps = "4",
                cfgScale = "1.0",
                sampler = "euler_a",
                seed = "42",
                selectedPreset = "Ideogram",
                compositionSummary = "background: studio",
                selectedCompositionElement = "obj desc=\"chair\"",
            ),
        )

        assertEquals("system", chat.first().role)
        assertTrue(chat.first().content.contains("prompt_mode: JSON"))
        assertTrue(chat.first().content.contains("selected_composition_element: obj"))
        assertTrue(chat.first().content.contains("composition_summary: background: studio"))
        assertEquals(listOf("assistant", "user", "assistant"), chat.drop(1).map { it.role })
    }
}
