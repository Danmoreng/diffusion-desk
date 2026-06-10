package com.diffusiondesk.desktop.screens

import com.diffusiondesk.desktop.viewmodel.IDEOGRAM_BBOX_GRID
import kotlin.math.roundToInt

internal const val IDEOGRAM_COORDINATE_MAX = 1000

internal data class CanvasRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal enum class CompositionResizeHandle {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

internal fun fitCanvasRect(
    containerWidth: Float,
    containerHeight: Float,
    contentWidth: Int,
    contentHeight: Int,
): CanvasRect {
    val safeContainerWidth = containerWidth.coerceAtLeast(0f)
    val safeContainerHeight = containerHeight.coerceAtLeast(0f)
    val aspectRatio = contentWidth.coerceAtLeast(1).toFloat() / contentHeight.coerceAtLeast(1).toFloat()
    val widthAtFullHeight = safeContainerHeight * aspectRatio
    val fittedWidth: Float
    val fittedHeight: Float

    if (widthAtFullHeight <= safeContainerWidth) {
        fittedWidth = widthAtFullHeight
        fittedHeight = safeContainerHeight
    } else {
        fittedWidth = safeContainerWidth
        fittedHeight = safeContainerWidth / aspectRatio
    }

    val left = (safeContainerWidth - fittedWidth) / 2f
    val top = (safeContainerHeight - fittedHeight) / 2f
    return CanvasRect(
        left = left,
        top = top,
        right = left + fittedWidth,
        bottom = top + fittedHeight,
    )
}

internal fun ideogramBboxToCanvasRect(
    values: List<Int>,
    canvasWidth: Float,
    canvasHeight: Float,
): CanvasRect? {
    val bbox = normalizeCompositionBbox(values) ?: return null
    val normalizedWidth = canvasWidth.coerceAtLeast(0f) / IDEOGRAM_COORDINATE_MAX
    val normalizedHeight = canvasHeight.coerceAtLeast(0f) / IDEOGRAM_COORDINATE_MAX
    return CanvasRect(
        left = bbox[1] * normalizedWidth,
        top = bbox[0] * normalizedHeight,
        right = bbox[3] * normalizedWidth,
        bottom = bbox[2] * normalizedHeight,
    )
}

internal fun canvasDeltaToIdeogram(delta: Float, canvasExtent: Float): Int {
    if (canvasExtent <= 0f) return 0
    return ((delta / canvasExtent) * IDEOGRAM_COORDINATE_MAX).roundToInt()
}

internal fun normalizeCompositionBbox(values: List<Int>): List<Int>? {
    if (values.size != 4) return null
    val yMin = snapCompositionCoord(values[0])
    val xMin = snapCompositionCoord(values[1])
    val yMax = snapCompositionCoord(values[2])
    val xMax = snapCompositionCoord(values[3])
    val minSize = IDEOGRAM_BBOX_GRID
    return listOf(
        yMin.coerceIn(0, (yMax - minSize).coerceAtLeast(0)),
        xMin.coerceIn(0, (xMax - minSize).coerceAtLeast(0)),
        yMax.coerceIn((yMin + minSize).coerceAtMost(IDEOGRAM_COORDINATE_MAX), IDEOGRAM_COORDINATE_MAX),
        xMax.coerceIn((xMin + minSize).coerceAtMost(IDEOGRAM_COORDINATE_MAX), IDEOGRAM_COORDINATE_MAX),
    )
}

internal fun moveCompositionBbox(values: List<Int>, deltaX: Int, deltaY: Int): List<Int>? {
    val bbox = normalizeCompositionBbox(values) ?: return null
    val boxHeight = (bbox[2] - bbox[0]).coerceAtLeast(IDEOGRAM_BBOX_GRID)
    val boxWidth = (bbox[3] - bbox[1]).coerceAtLeast(IDEOGRAM_BBOX_GRID)
    val nextYMin = snapCompositionCoord(bbox[0] + deltaY).coerceIn(0, IDEOGRAM_COORDINATE_MAX - boxHeight)
    val nextXMin = snapCompositionCoord(bbox[1] + deltaX).coerceIn(0, IDEOGRAM_COORDINATE_MAX - boxWidth)
    return listOf(nextYMin, nextXMin, nextYMin + boxHeight, nextXMin + boxWidth)
}

internal fun resizeCompositionBbox(
    values: List<Int>,
    deltaX: Int,
    deltaY: Int,
    handle: CompositionResizeHandle,
): List<Int>? {
    val bbox = normalizeCompositionBbox(values) ?: return null
    val minSize = IDEOGRAM_BBOX_GRID
    var yMin = bbox[0]
    var xMin = bbox[1]
    var yMax = bbox[2]
    var xMax = bbox[3]
    when (handle) {
        CompositionResizeHandle.TopLeft -> {
            yMin = snapCompositionCoord(bbox[0] + deltaY).coerceIn(0, yMax - minSize)
            xMin = snapCompositionCoord(bbox[1] + deltaX).coerceIn(0, xMax - minSize)
        }
        CompositionResizeHandle.TopRight -> {
            yMin = snapCompositionCoord(bbox[0] + deltaY).coerceIn(0, yMax - minSize)
            xMax = snapCompositionCoord(bbox[3] + deltaX).coerceIn(xMin + minSize, IDEOGRAM_COORDINATE_MAX)
        }
        CompositionResizeHandle.BottomLeft -> {
            yMax = snapCompositionCoord(bbox[2] + deltaY).coerceIn(yMin + minSize, IDEOGRAM_COORDINATE_MAX)
            xMin = snapCompositionCoord(bbox[1] + deltaX).coerceIn(0, xMax - minSize)
        }
        CompositionResizeHandle.BottomRight -> {
            yMax = snapCompositionCoord(bbox[2] + deltaY).coerceIn(yMin + minSize, IDEOGRAM_COORDINATE_MAX)
            xMax = snapCompositionCoord(bbox[3] + deltaX).coerceIn(xMin + minSize, IDEOGRAM_COORDINATE_MAX)
        }
    }
    return listOf(yMin, xMin, yMax, xMax)
}

private fun snapCompositionCoord(value: Int): Int =
    ((value.toFloat() / IDEOGRAM_BBOX_GRID).roundToInt() * IDEOGRAM_BBOX_GRID)
        .coerceIn(0, IDEOGRAM_COORDINATE_MAX)
