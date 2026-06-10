package com.diffusiondesk.desktop.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompositionCanvasGeometryTest {
    @Test
    fun fitsLandscapeContentInsideSquareContainer() {
        val rect = fitCanvasRect(1000f, 1000f, 1600, 900)

        assertRectEquals(CanvasRect(0f, 218.75f, 1000f, 781.25f), rect)
    }

    @Test
    fun fitsPortraitContentInsideLandscapeContainer() {
        val rect = fitCanvasRect(1200f, 600f, 900, 1600)

        assertRectEquals(CanvasRect(431.25f, 0f, 768.75f, 600f), rect)
    }

    @Test
    fun fitsSquareContentInsidePortraitContainer() {
        val rect = fitCanvasRect(600f, 900f, 1024, 1024)

        assertRectEquals(CanvasRect(0f, 150f, 600f, 750f), rect)
    }

    @Test
    fun mapsNormalizedBoundingBoxToCanvasCoordinates() {
        val rect = ideogramBboxToCanvasRect(
            values = listOf(100, 200, 700, 800),
            canvasWidth = 500f,
            canvasHeight = 1000f,
        )

        assertRectEquals(CanvasRect(100f, 100f, 400f, 700f), rect!!)
    }

    @Test
    fun rejectsMalformedBoundingBox() {
        assertNull(ideogramBboxToCanvasRect(listOf(0, 100, 200), 500f, 500f))
    }

    @Test
    fun convertsCanvasDragToIdeogramCoordinates() {
        assertEquals(250, canvasDeltaToIdeogram(125f, 500f))
        assertEquals(-100, canvasDeltaToIdeogram(-50f, 500f))
        assertEquals(0, canvasDeltaToIdeogram(50f, 0f))
    }

    @Test
    fun movementSnapsAndStaysInsideCanvas() {
        assertEquals(
            listOf(800, 0, 1000, 300),
            moveCompositionBbox(listOf(100, 100, 300, 400), deltaX = -500, deltaY = 900),
        )
    }

    @Test
    fun resizingPreservesMinimumSizeAndCanvasBounds() {
        assertEquals(
            listOf(100, 100, 110, 110),
            resizeCompositionBbox(
                values = listOf(100, 100, 300, 400),
                deltaX = -500,
                deltaY = -500,
                handle = CompositionResizeHandle.BottomRight,
            ),
        )
    }

    private fun assertRectEquals(expected: CanvasRect, actual: CanvasRect) {
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }
}
