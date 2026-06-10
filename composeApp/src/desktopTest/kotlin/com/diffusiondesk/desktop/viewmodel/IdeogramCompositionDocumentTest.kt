package com.diffusiondesk.desktop.viewmodel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IdeogramCompositionDocumentTest {
    private val source = """
        {
          "high_level_description": "Scene",
          "custom_root": {"keep": true},
          "style_description": {
            "aesthetics": "clean",
            "lighting": "soft",
            "medium": "photo",
            "photo": "editorial",
            "custom_style": 7
          },
          "compositional_deconstruction": {
            "background": "studio",
            "custom_composition": "keep",
            "elements": [{
              "type": "obj",
              "bbox": [0, 0, 500, 500],
              "desc": "subject",
              "custom_element": [1, 2]
            }]
          }
        }
    """.trimIndent()

    @Test
    fun mutationPreservesUnknownFields() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val changed = document.applyMutation(
            CompositionMutation.UpdateElementDescription(0, "detailed subject"),
        ).getOrThrow()
        val root = Json.parseToJsonElement(changed.serialize()).jsonObject

        assertNotNull(root["custom_root"])
        assertNotNull(root["style_description"]?.jsonObject?.get("custom_style"))
        assertNotNull(root["compositional_deconstruction"]?.jsonObject?.get("custom_composition"))
        assertNotNull(root["compositional_deconstruction"]?.jsonObject
            ?.get("elements")
            ?.let { it as kotlinx.serialization.json.JsonArray }
            ?.first()
            ?.jsonObject
            ?.get("custom_element"))
        assertEquals("detailed subject", changed.elements.first().description)
    }

    @Test
    fun switchingStyleModeRemovesTheOtherMode() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val changed = document.applyMutation(
            CompositionMutation.UpdateStyleField(IdeogramStyleField.ArtStyle, "linocut"),
        ).getOrThrow()

        assertEquals(null, changed.style.photo)
        assertEquals("linocut", changed.style.artStyle)
    }
}
