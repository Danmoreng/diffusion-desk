package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.AssistantToolContext
import com.diffusiondesk.desktop.core.AssistantToolRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantViewModelTest {
    @Test
    fun assistantMessagesIncludeContextAndRecentHistory() {
        val messages = listOf(
            AssistantMessage(AssistantMessageRole.Assistant, "Welcome"),
            AssistantMessage(AssistantMessageRole.User, "Make this sharper"),
            AssistantMessage(AssistantMessageRole.Assistant, "Try stronger material detail."),
        )

        val chat = buildAssistantAgentMessages(
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

    @Test
    fun assistantMessagesAttachOnlyNewestImageToLlmRequest() {
        val olderImage = AssistantImageAttachment(
            name = "older.png",
            dataUri = "data:image/png;base64,older",
            thumbnailDataUri = "data:image/png;base64,older-thumb",
            width = 100,
            height = 100,
        )
        val newestImage = AssistantImageAttachment(
            name = "newest.png",
            dataUri = "data:image/png;base64,newest",
            thumbnailDataUri = "data:image/png;base64,newest-thumb",
            width = 160,
            height = 90,
        )
        val messages = listOf(
            AssistantMessage(AssistantMessageRole.User, "First image", imageAttachment = olderImage),
            AssistantMessage(AssistantMessageRole.Assistant, "Looks good."),
            AssistantMessage(AssistantMessageRole.User, "Use this one instead", imageAttachment = newestImage),
        )

        val chat = buildAssistantAgentMessages(
            messages = messages,
            context = AssistantContextSnapshot(
                screen = "Generate",
                promptMode = "Prompt",
                prompt = "",
                negativePrompt = "",
                width = "160",
                height = "90",
                steps = "20",
                cfgScale = "7.0",
                sampler = "euler_a",
                seed = "42",
                selectedPreset = "Ideogram",
                compositionSummary = "none",
                selectedCompositionElement = "none",
            ),
        )

        assertNull(chat[1].imageDataUri)
        assertEquals("data:image/png;base64,newest", chat.last().imageDataUri)
        assertTrue(chat.first().content.contains("attached_or_recent_reference_image: newest.png, 160 x 90 (16:9, landscape)"))
    }

    @Test
    fun toolRegistryFiltersToolsByPromptModeAndCompositionState() {
        val textTools = AssistantToolRegistry.toolsFor(
            AssistantToolContext(
                promptMode = "Text",
                hasStructuredComposition = false,
                hasSelectedCompositionElement = false,
            ),
        ).toolNames()
        assertTrue("set_prompt" in textTools)
        assertFalse("generate_structured_prompt" in textTools)
        assertFalse("update_high_level_description" in textTools)

        val initialJsonTools = AssistantToolRegistry.toolsFor(
            AssistantToolContext(
                promptMode = "JSON",
                hasStructuredComposition = false,
                hasSelectedCompositionElement = false,
            ),
        ).toolNames()
        assertTrue("generate_structured_prompt" in initialJsonTools)
        assertFalse("replace_structured_prompt" in initialJsonTools)
        assertFalse("update_selected_element_description" in initialJsonTools)

        val existingCompositionTools = AssistantToolRegistry.toolsFor(
            AssistantToolContext(
                promptMode = "JSON",
                hasStructuredComposition = true,
                hasSelectedCompositionElement = true,
            ),
        ).toolNames()
        assertFalse("generate_structured_prompt" in existingCompositionTools)
        assertTrue("replace_structured_prompt" in existingCompositionTools)
        assertTrue("update_high_level_description" in existingCompositionTools)
        assertTrue("update_selected_element_description" in existingCompositionTools)
        assertTrue("update_generation_settings" in existingCompositionTools)
    }
}

private fun JsonArray.toolNames(): Set<String> =
    mapNotNull { tool ->
        tool.jsonObject["function"]
            ?.jsonObject
            ?.get("name")
            ?.jsonPrimitive
            ?.content
    }.toSet()
