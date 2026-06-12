package com.diffusiondesk.desktop.composition

import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionElement
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompositionActionExecutorTest {
    @Test
    fun parsesSingleValueFieldPatch() {
        assertEquals("Detailed studio background", parseCompositionFieldPatch("""{"value":"Detailed studio background"}"""))
    }

    @Test
    fun rejectsAdditionalFieldPatchProperties() {
        assertFailsWith<IllegalArgumentException> {
            parseCompositionFieldPatch("""{"value":"Changed","background":"Also changed"}""")
        }
    }

    @Test
    fun parsesAndNormalizesPalettePatch() {
        assertEquals(
            listOf("#AABBCC", "#102030"),
            parseCompositionPalettePatch("""{"colors":["#aabbcc","#102030","#AABBCC"]}""", maxColors = 5),
        )
    }

    @Test
    fun rejectsPaletteAboveTargetLimit() {
        assertFailsWith<IllegalArgumentException> {
            parseCompositionPalettePatch("""{"colors":["#000000","#111111"]}""", maxColors = 1)
        }
    }

    @Test
    fun cropsNormalizedBoundingBoxWithContext() {
        val source = BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB)
        val crop = source.cropIdeogramBbox(listOf(200, 250, 800, 750), marginFraction = 0.1)

        assertEquals(600, crop.width)
        assertEquals(360, crop.height)
    }

    @Test
    fun parsesStylePatchForCurrentModeOnly() {
        val patch = parseStylePatch(
            """{"aesthetics":"cinematic","lighting":"rim light","medium":"digital photo","photo":"editorial","color_palette":["#112233"]}""",
            modeKey = "photo",
        )

        assertEquals("editorial", patch.photo)
        assertEquals(null, patch.artStyle)
        assertEquals(listOf("#112233"), patch.colorPalette)
    }

    @Test
    fun rejectsStylePatchThatChangesMode() {
        assertFailsWith<IllegalArgumentException> {
            parseStylePatch(
                """{"aesthetics":"clean","lighting":"soft","medium":"ink","art_style":"poster"}""",
                modeKey = "photo",
            )
        }
    }

    @Test
    fun parsesCompleteCompositionPlacementPatch() {
        val patch = parseCompositionPatch(
            """{"background":"city","placements":[{"index":1,"bbox":[100,500,500,900]},{"index":0,"bbox":[100,100,500,400]}]}""",
            elementCount = 2,
        )

        assertEquals("city", patch.background)
        assertEquals(listOf(100, 100, 500, 400), patch.elementBboxes[0])
    }

    @Test
    fun rejectsIncompleteCompositionPlacementPatch() {
        assertFailsWith<IllegalArgumentException> {
            parseCompositionPatch(
                """{"background":"city","placements":[{"index":0,"bbox":[100,100,500,400]}]}""",
                elementCount = 2,
            )
        }
    }

    @Test
    fun parsesElementAndPlacementPatches() {
        val element = parseElementPatch(
            """{"type":"text","bbox":[100,200,300,800],"text":"HELLO","desc":"bold title","color_palette":["#FFFFFF"]}""",
            expectedType = "text",
        )

        assertEquals("HELLO", element.text)
        assertEquals(listOf(100, 200, 300, 800), parsePlacementPatch("""{"bbox":[100,200,300,800]}"""))
        assertTrue(element.colorPalette.contains("#FFFFFF"))
    }

    @Test
    fun rejectsElementTypeChangeAndInvalidBounds() {
        assertFailsWith<IllegalArgumentException> {
            parseElementPatch("""{"type":"obj","desc":"subject"}""", expectedType = "text")
        }
        assertFailsWith<IllegalArgumentException> {
            parsePlacementPatch("""{"bbox":[500,200,300,800]}""")
        }
    }

    @Test
    fun regenerationChangesDescriptionAndPaletteButPreservesPlacementAndText() {
        val current = IdeogramCompositionElement(
            type = "text",
            bbox = listOf(100, 200, 300, 800),
            description = "plain title",
            text = "HELLO",
            colorPalette = listOf("#FFFFFF"),
        )
        val candidate = IdeogramCompositionElement(
            type = "text",
            bbox = emptyList(),
            description = "embossed metallic title",
            text = "CHANGED",
            colorPalette = listOf("#FFD700", "#111111"),
        )

        val regenerated = prepareRegeneratedElement(current, candidate)

        assertEquals(current.bbox, regenerated.bbox)
        assertEquals("HELLO", regenerated.text)
        assertEquals("embossed metallic title", regenerated.description)
        assertEquals(listOf("#FFD700", "#111111"), regenerated.colorPalette)
    }

    @Test
    fun regenerationRejectsUnchangedDescriptionOrPalette() {
        val current = IdeogramCompositionElement("obj", emptyList(), "subject", null, listOf("#FFFFFF"))

        assertFailsWith<IllegalArgumentException> {
            prepareRegeneratedElement(current, current.copy(colorPalette = listOf("#000000")))
        }
        assertFailsWith<IllegalArgumentException> {
            prepareRegeneratedElement(current, current.copy(description = "different subject"))
        }
    }

    @Test
    fun parsesElementDocumentAndDeleteSummaryPatches() {
        val patch = parseElementDocumentPatch(
            """{"high_level_description":"A scene with a gold title","element":{"type":"text","text":"HELLO","desc":"gold title","color_palette":["#FFD700"]}}""",
            expectedType = "text",
        )

        assertEquals("A scene with a gold title", patch.highLevelDescription)
        assertEquals("HELLO", patch.element.text)
        assertEquals(
            "A quiet empty studio",
            parseHighLevelPatch("""{"high_level_description":"A quiet empty studio"}"""),
        )
    }

    @Test
    fun rejectsUnchangedHighLevelDescription() {
        assertFailsWith<IllegalArgumentException> {
            requireUpdatedHighLevel("A scene with a subject", "A scene with a subject")
        }
        requireUpdatedHighLevel("A scene with a subject", "A scene with a different subject")
    }

}
