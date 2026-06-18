package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.composition.mutationForImprovedValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun serializerUsesCanonicalPhotoAndElementOrder() {
        val serialized = parseIdeogramCompositionDocument(source).getOrThrow().serializeForBackend()

        assertTrue(serialized.indexOf("\"aesthetics\"") < serialized.indexOf("\"lighting\""))
        assertTrue(serialized.indexOf("\"lighting\"") < serialized.indexOf("\"photo\""))
        assertTrue(serialized.indexOf("\"photo\"") < serialized.indexOf("\"medium\""))
        assertTrue(serialized.indexOf("\"type\"") < serialized.indexOf("\"bbox\""))
        assertTrue(serialized.indexOf("\"bbox\"") < serialized.indexOf("\"desc\""))
    }

    @Test
    fun boundingBoxCanBeRemoved() {
        val changed = parseIdeogramCompositionDocument(source).getOrThrow()
            .applyMutation(CompositionMutation.UpdateElementBbox(0, null))
            .getOrThrow()

        assertEquals(emptyList(), changed.elements.first().bbox)
        assertFalse(changed.serializeForBackend().contains("\"bbox\""))
    }

    @Test
    fun compactSerializerPreservesLiteralUnicode() {
        val changed = parseIdeogramCompositionDocument(source).getOrThrow()
            .applyMutation(CompositionMutation.UpdateHighLevelDescription("Café in Köln"))
            .getOrThrow()
        val serialized = changed.serializeForBackend()

        assertTrue(serialized.contains("Café in Köln"))
        assertFalse(serialized.contains("\\u"))
        assertFalse(serialized.contains("\n"))
    }

    @Test
    fun elementsCanBeAddedAndRemoved() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val added = document.applyMutation(CompositionMutation.AddElement("text")).getOrThrow()

        assertEquals(2, added.elements.size)
        assertEquals("text", added.elements.last().type)
        assertEquals(emptyList(), added.elements.last().bbox)
        assertEquals("", added.elements.last().description)
        assertEquals("", added.elements.last().text)

        val removed = added.applyMutation(CompositionMutation.RemoveElement(0)).getOrThrow()
        assertEquals(1, removed.elements.size)
        assertEquals("text", removed.elements.first().type)
    }

    @Test
    fun additionalFieldsCanBeAddedUpdatedAndRemoved() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val added = document.applyMutation(
            CompositionMutation.AddAdditionalField("style_description.custom_note", "\"first\""),
        ).getOrThrow()
        assertTrue(added.additionalFields.any { it.path == "style_description.custom_note" && it.jsonValue == "\"first\"" })

        val updated = added.applyMutation(
            CompositionMutation.UpdateAdditionalField("style_description.custom_note", "\"second\""),
        ).getOrThrow()
        assertTrue(updated.additionalFields.any { it.path == "style_description.custom_note" && it.jsonValue == "\"second\"" })

        val removed = updated.applyMutation(
            CompositionMutation.RemoveAdditionalField("style_description.custom_note"),
        ).getOrThrow()
        assertFalse(removed.additionalFields.any { it.path == "style_description.custom_note" })
    }

    @Test
    fun newHistoryEntryDropsRedoBranch() {
        val history = listOf("one", "two", "three")
        val committed = commitCompositionHistory(history, index = 1, value = "replacement")

        assertEquals(listOf("one", "two", "replacement"), committed.entries)
        assertEquals(2, committed.index)
    }

    @Test
    fun committingSameHistoryValueDoesNotDuplicateIt() {
        val committed = commitCompositionHistory(listOf("one", "two"), index = 1, value = "two")

        assertEquals(listOf("one", "two"), committed.entries)
        assertEquals(1, committed.index)
    }

    @Test
    fun improvedFieldMutationChangesOnlyItsTarget() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val mutation = mutationForImprovedValue(
            CompositionImproveTarget.ElementDescription(0),
            "More detailed subject",
        )
        val changed = document.applyMutation(mutation).getOrThrow()

        assertEquals("More detailed subject", changed.elements.first().description)
        assertEquals(document.background, changed.background)
        assertEquals(document.style, changed.style)
    }

    @Test
    fun stylePatchIsAtomicAndPreservesUnknownFields() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val changed = document.applyMutation(
            CompositionMutation.ReplaceStyle(
                IdeogramStylePatch("dramatic", "rim", "photography", "fashion", null, listOf("#112233")),
            ),
        ).getOrThrow()

        assertEquals("dramatic", changed.style.aesthetics)
        assertEquals(document.background, changed.background)
        assertTrue(changed.additionalFields.any { it.path == "style_description.custom_style" })
    }

    @Test
    fun compositionPatchOnlyChangesBackgroundAndPlacements() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val changed = document.applyMutation(
            CompositionMutation.ReplaceComposition(
                IdeogramCompositionPatch("night city", listOf(listOf(100, 200, 700, 800))),
            ),
        ).getOrThrow()

        assertEquals("night city", changed.background)
        assertEquals(listOf(100, 200, 700, 800), changed.elements.first().bbox)
        assertEquals(document.elements.first().description, changed.elements.first().description)
        assertEquals(document.style, changed.style)
    }

    @Test
    fun generatedElementCanReplaceOrAppendAsOneMutation() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val replacement = IdeogramCompositionElement("obj", listOf(0, 0, 500, 500), "new subject", null, listOf("#ABCDEF"))
        val replaced = document.applyMutation(CompositionMutation.ReplaceElement(0, replacement)).getOrThrow()

        assertEquals("new subject", replaced.elements.first().description)
        assertTrue(replaced.additionalFields.any { it.path == "compositional_deconstruction.elements[0].custom_element" })

        val added = replaced.applyMutation(
            CompositionMutation.AddGeneratedElement(
                IdeogramCompositionElement("text", listOf(700, 100, 900, 900), "headline", "HELLO", emptyList()),
            ),
        ).getOrThrow()
        assertEquals(2, added.elements.size)
        assertEquals("HELLO", added.elements.last().text)
    }

    @Test
    fun elementAndHighLevelChangesAreAppliedAtomically() {
        val document = parseIdeogramCompositionDocument(source).getOrThrow()
        val replacement = IdeogramCompositionElement("obj", listOf(0, 0, 500, 500), "new subject", null, listOf("#ABCDEF"))
        val replaced = document.applyMutation(
            CompositionMutation.ReplaceElementAndHighLevel(
                0,
                IdeogramElementDocumentPatch("Scene with a new subject", replacement),
            ),
        ).getOrThrow()

        assertEquals("Scene with a new subject", replaced.highLevelDescription)
        assertEquals("new subject", replaced.elements.first().description)

        val added = replaced.applyMutation(
            CompositionMutation.AddElementAndHighLevel(
                IdeogramElementDocumentPatch(
                    "Scene with a new subject and title",
                    IdeogramCompositionElement("text", emptyList(), "title", "HELLO", listOf("#FFFFFF")),
                ),
            ),
        ).getOrThrow()
        assertEquals(2, added.elements.size)
        assertEquals("Scene with a new subject and title", added.highLevelDescription)

        val removed = added.applyMutation(
            CompositionMutation.RemoveElementAndUpdateHighLevel(1, "Scene with a new subject"),
        ).getOrThrow()
        assertEquals(1, removed.elements.size)
        assertEquals("Scene with a new subject", removed.highLevelDescription)
    }
}
