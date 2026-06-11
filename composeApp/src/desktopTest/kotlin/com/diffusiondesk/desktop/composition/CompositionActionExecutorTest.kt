package com.diffusiondesk.desktop.composition

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
