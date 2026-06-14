package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.ModelSummary
import com.diffusiondesk.desktop.viewmodel.UpscaleSourceImage
import com.diffusiondesk.desktop.viewmodel.UpscaleUiState
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.DefaultButton as Button
import org.jetbrains.jewel.ui.component.Text

@Composable
fun UpscaleScreen(
    state: UpscaleUiState,
    backendState: BackendUiState,
    outputDir: String,
    onSelectImage: (File) -> Unit,
    onReloadModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onFactorChange: (Double) -> Unit,
    onUpscale: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(DeskScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
    ) {
        DeskPanel(
            modifier = Modifier
                .width(430.dp)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
            ) {
                UpscaleSectionTitle("Image")
                Button(
                    onClick = { chooseImageFile()?.let(onSelectImage) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    UpscaleButtonContent(Icons.Default.FolderOpen, "Choose image")
                }
                state.source?.let { source ->
                    UpscaleSourceSummary(source)
                } ?: UpscaleHint("Choose a source image or send one here from the Gallery.")

                UpscaleSectionTitle("ESRGAN model")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onReloadModels,
                        enabled = !state.isLoadingModels && backendState.status == BackendStatus.Ready,
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        UpscaleButtonContent(Icons.Default.Refresh, "Reload models")
                    }
                }
                if (state.isLoadingModels) {
                    UpscaleHint("Loading ESRGAN models...")
                } else if (state.upscaleModels.isEmpty()) {
                    UpscaleHint("No ESRGAN models found in the models/esrgan folder.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.upscaleModels.forEach { model ->
                            UpscaleModelRow(
                                model = model,
                                selected = model.id == state.selectedModelId,
                                onClick = { onSelectModel(model.id) },
                            )
                        }
                    }
                }

                UpscaleSectionTitle("Scale")
                UpscaleScaleSlider(
                    factor = state.factor,
                    onFactorChange = onFactorChange,
                )
                val target = state.targetWidth?.let { width ->
                    val height = state.targetHeight ?: 0
                    "$width x $height"
                } ?: "-"
                UpscaleHint("Target size: $target")

                Button(
                    onClick = onUpscale,
                    enabled = state.canUpscale && backendState.status == BackendStatus.Ready,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    UpscaleButtonContent(Icons.Default.CropFree, if (state.isUpscaling) "Upscaling..." else "Upscale")
                }

                state.message.takeIf { it.isNotBlank() }?.let {
                    UpscaleHint(it)
                }
                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        DeskPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
            UpscaleComparisonPane(
                source = state.source,
                result = state.result,
                outputDir = outputDir,
                loading = state.isUpscaling,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun UpscaleSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun UpscaleHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UpscaleSourceSummary(source: UpscaleSourceImage) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha),
        shape = RoundedCornerShape(DeskControlCornerRadius),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(source.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${source.width} x ${source.height}", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UpscaleModelRow(
    model: ModelSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(DeskControlCornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    if (model.loaded || model.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(999.dp),
                ),
        )
        Text(model.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UpscaleButtonContent(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UpscaleScaleSlider(
    factor: Double,
    onFactorChange: (Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpscaleHint("Manual upscale factor")
            Text(
                text = "${"%.2f".format(Locale.US, factor)}x",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = factor.toFloat().coerceIn(1f, 4f),
            onValueChange = { value ->
                onFactorChange(((value / 0.05f).roundToInt() * 0.05f).toDouble())
            },
            valueRange = 1f..4f,
            steps = 59,
        )
    }
}

@Composable
private fun UpscaleComparisonPane(
    source: UpscaleSourceImage?,
    result: GeneratedImage?,
    outputDir: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    var comparePosition by remember { mutableFloatStateOf(0.5f) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Preview", fontWeight = FontWeight.SemiBold)
            Text(
                text = if (result == null) "Original" else "Original / Upscaled",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ImageContextMenuArea(
            images = buildList {
                if (result != null) {
                    add(result.toImageContextMenuData(outputDir, fallbackFileName = "upscaled-image.png"))
                } else {
                    source?.let { add(it.toImageContextMenuData(labelPrefix = "")) }
                }
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    source == null -> UpscaleHint("No image selected.")
                    result == null -> Image(
                        bitmap = source.bitmap,
                        contentDescription = "Original image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    else -> ImageComparisonSlider(
                        source = source,
                        result = result,
                        comparePosition = comparePosition,
                        onComparePositionChange = { comparePosition = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ImageComparisonSlider(
    source: UpscaleSourceImage,
    result: GeneratedImage,
    comparePosition: Float,
    onComparePositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentComparePosition by rememberUpdatedState(comparePosition)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val imageAspect = result.bufferedImage.width.coerceAtLeast(1).toFloat() /
            result.bufferedImage.height.coerceAtLeast(1).toFloat()
        val containerAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val imageWidth = if (containerAspect > imageAspect) maxHeight * imageAspect else maxWidth
        val imageHeight = if (containerAspect > imageAspect) maxHeight else maxWidth / imageAspect
        val splitWidth = imageWidth * comparePosition.coerceIn(0f, 1f)
        val handleHitWidth = 28.dp
        val dividerColor = MaterialTheme.colorScheme.primary
        var isDraggingHandle by remember { mutableStateOf(false) }
        var isHoveringHandle by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .width(imageWidth)
                .height(imageHeight)
                .clipToBounds(),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
                val splitX = size.width * currentComparePosition.coerceIn(0f, 1f)
                drawImage(
                    image = result.bitmap,
                    dstSize = dstSize,
                )
                clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                    drawImage(
                        image = source.bitmap,
                        dstSize = dstSize,
                    )
                }
                drawLine(
                    color = dividerColor,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxHeight()
                    .onPointerEvent(PointerEventType.Move) { event ->
                        val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        val imageWidthPx = with(density) { imageWidth.toPx() }.coerceAtLeast(1f)
                        val splitX = imageWidthPx * currentComparePosition.coerceIn(0f, 1f)
                        val hitRadius = with(density) { handleHitWidth.toPx() } / 2f
                        isHoveringHandle = kotlin.math.abs(position.x - splitX) <= hitRadius
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        isHoveringHandle = false
                    }
                    .then(
                        if (isHoveringHandle || isDraggingHandle) {
                            Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(imageWidth, handleHitWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val imageWidthPx = imageWidth.toPx().coerceAtLeast(1f)
                                val splitX = imageWidthPx * currentComparePosition.coerceIn(0f, 1f)
                                isDraggingHandle = kotlin.math.abs(offset.x - splitX) <= handleHitWidth.toPx() / 2f
                            },
                            onDragEnd = { isDraggingHandle = false },
                            onDragCancel = { isDraggingHandle = false },
                        ) { change, _ ->
                            if (!isDraggingHandle) return@detectDragGestures
                            val imageWidthPx = imageWidth.toPx().coerceAtLeast(1f)
                            val next = change.position.x / imageWidthPx
                            onComparePositionChange(next.coerceIn(0f, 1f))
                            change.consume()
                        }
                    },
            )
        }
    }
}

private fun chooseImageFile(): File? {
    val dialog = FileDialog(null as Frame?, "Choose image", FileDialog.LOAD).apply {
        isVisible = true
    }
    val file = dialog.file ?: return null
    val directory = dialog.directory ?: return null
    return File(directory, file)
}

private fun UpscaleSourceImage.toImageContextMenuData(labelPrefix: String): ImageContextMenuData {
    return ImageContextMenuData(
        labelPrefix = labelPrefix,
        file = file,
        bytes = bytes,
        bufferedImage = bufferedImage,
        defaultFileName = name,
        extension = file.extension.ifBlank { "png" },
    )
}
