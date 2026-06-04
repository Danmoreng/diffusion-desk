package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.vector.ImageVector
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.GenerationStatus
import com.diffusiondesk.desktop.viewmodel.GenerationUiState
import kotlin.math.roundToInt
import org.jetbrains.jewel.ui.component.DefaultButton as Button
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text

@Composable
fun GenerateScreen(
    state: GenerationUiState,
    backendState: BackendUiState,
    samplerOptions: List<String>,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onBatchCountChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onRandomizeSeed: () -> Unit,
    onReuseLastSeed: () -> Unit,
    onSwapDimensions: () -> Unit,
    onApplyAspectRatio: (Int, Int) -> Unit,
    onScaleResolution: (Int) -> Unit,
    onResetToPresetDefaults: () -> Unit,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onLeftPanelWidthChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
        ) {
            val minPanelWidth = 380.dp
            val maxPanelWidth = minOf(900.dp, (maxWidth - 280.dp).coerceAtLeast(minPanelWidth))
            val density = LocalDensity.current
            var panelWidthDp by remember { mutableStateOf(state.leftPanelWidthDp.toFloat()) }
            var isDraggingPanel by remember { mutableStateOf(false) }
            var dragStartWidthPx by remember { mutableStateOf(0f) }
            var draggedPx by remember { mutableStateOf(0f) }
            val panelWidth = panelWidthDp.dp.coerceIn(minPanelWidth, maxPanelWidth)
            val currentPanelWidthPx by rememberUpdatedState(with(density) { panelWidth.toPx() })
            val currentPanelWidthDp by rememberUpdatedState(panelWidthDp)

            LaunchedEffect(state.leftPanelWidthDp, minPanelWidth, maxPanelWidth) {
                if (!isDraggingPanel) {
                    panelWidthDp = state.leftPanelWidthDp.dp.coerceIn(minPanelWidth, maxPanelWidth).value
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                GenerationPanel(
                    state = state,
                    samplerOptions = samplerOptions,
                    onPromptChange = onPromptChange,
                    onNegativePromptChange = onNegativePromptChange,
                    onWidthChange = onWidthChange,
                    onHeightChange = onHeightChange,
                    onStepsChange = onStepsChange,
                    onCfgScaleChange = onCfgScaleChange,
                    onSeedChange = onSeedChange,
                    onBatchCountChange = onBatchCountChange,
                    onSamplerChange = onSamplerChange,
                    onRandomizeSeed = onRandomizeSeed,
                    onReuseLastSeed = onReuseLastSeed,
                    onSwapDimensions = onSwapDimensions,
                    onApplyAspectRatio = onApplyAspectRatio,
                    onScaleResolution = onScaleResolution,
                    onResetToPresetDefaults = onResetToPresetDefaults,
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight(),
                )

                Splitter(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .pointerInput(minPanelWidth, maxPanelWidth) {
                            detectDragGestures(
                                onDragStart = {
                                    isDraggingPanel = true
                                    dragStartWidthPx = currentPanelWidthPx
                                    draggedPx = 0f
                                },
                                onDragEnd = {
                                    isDraggingPanel = false
                                    onLeftPanelWidthChange(currentPanelWidthDp.roundToInt())
                                },
                                onDragCancel = {
                                    isDraggingPanel = false
                                    onLeftPanelWidthChange(currentPanelWidthDp.roundToInt())
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggedPx += dragAmount.x
                                    val nextWidthPx = (dragStartWidthPx + draggedPx)
                                        .coerceIn(
                                            with(density) { minPanelWidth.toPx() },
                                            with(density) { maxPanelWidth.toPx() },
                                        )
                                    panelWidthDp = with(density) { nextWidthPx.toDp().value }
                                },
                            )
                        },
                )

                PreviewPanel(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        ActionBar(
            state = state,
            backendState = backendState,
            onGenerate = onGenerate,
            onToggleEndless = onToggleEndless,
            onGoBack = onGoBack,
            onGoForward = onGoForward,
        )
    }
}

@Composable
private fun Splitter(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun GenerationPanel(
    state: GenerationUiState,
    samplerOptions: List<String>,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onBatchCountChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onRandomizeSeed: () -> Unit,
    onReuseLastSeed: () -> Unit,
    onSwapDimensions: () -> Unit,
    onApplyAspectRatio: (Int, Int) -> Unit,
    onScaleResolution: (Int) -> Unit,
    onResetToPresetDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showNegativePrompt = (state.cfgScale.toDoubleOrNull() ?: 0.0) > 1.0

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label("Prompt")
            PaddedTextArea(
                value = state.prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 142.dp),
            )

            if (showNegativePrompt) {
                Label("Negative Prompt")
                PaddedTextArea(
                    value = state.negativePrompt,
                    onValueChange = onNegativePromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 74.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("Parameters")
                SubtleTextButton(
                    icon = Icons.Default.RestartAlt,
                    text = "Reset to defaults",
                    onClick = onResetToPresetDefaults,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField("Steps", state.steps, onStepsChange, Modifier.weight(0.8f))
                CompactTextField("Batch", state.batchCount, onBatchCountChange, Modifier.weight(0.8f))
                CompactTextField("Seed", state.seed, onSeedChange, Modifier.weight(1.1f))
                CompactIconButton(
                    icon = Icons.Default.Casino,
                    contentDescription = "Random seed",
                    onClick = onRandomizeSeed,
                )
                CompactIconButton(
                    icon = Icons.Default.Recycling,
                    contentDescription = "Reuse last seed",
                    onClick = onReuseLastSeed,
                    enabled = state.history.any { it.usedSeed != null },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField("Width", state.width, onWidthChange, Modifier.weight(1f))
                CompactIconButton(
                    icon = Icons.Default.SwapHoriz,
                    contentDescription = "Swap dimensions",
                    onClick = onSwapDimensions,
                )
                CompactTextField("Height", state.height, onHeightChange, Modifier.weight(1f))
                AspectRatioMenu(
                    width = state.width,
                    height = state.height,
                    onApplyAspectRatio = onApplyAspectRatio,
                    modifier = Modifier.weight(0.78f),
                )
            }
            ResolutionSlider(
                state = state,
                onScaleResolution = onScaleResolution,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField("CFG", state.cfgScale, onCfgScaleChange, Modifier.weight(1f))
                SamplerMenu(
                    value = state.sampler,
                    options = samplerOptions,
                    onChange = onSamplerChange,
                    modifier = Modifier.weight(1f),
                )
            }

            state.currentHistoryItem?.let { item ->
                Label("Selected Generation")
                Text(
                    text = when (item.status) {
                        GenerationStatus.Pending -> "Queued"
                        GenerationStatus.Processing -> "Generating"
                        GenerationStatus.Completed -> "Completed"
                        GenerationStatus.Failed -> "Failed"
                    },
                    color = when (item.status) {
                        GenerationStatus.Completed -> MaterialTheme.colorScheme.primary
                        GenerationStatus.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                item.generationTime?.let {
                    Text(
                        text = "Generated in ${"%.1f".format(it)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.message.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    state: GenerationUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isGenerating && state.currentHistoryItem?.status == GenerationStatus.Processing -> {
                ProgressCard(state)
            }
            state.images.isNotEmpty() -> {
                GeneratedImageGrid(state)
            }
            state.currentHistoryItem?.status == GenerationStatus.Pending -> {
                Text("Queued", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratedImageGrid(state: GenerationUiState) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val imageCount = state.images.size
        val columns = if (imageCount <= 1) 1 else 2
        val rows = ((imageCount + columns - 1) / columns).coerceAtLeast(1)
        val spacing = 10.dp
        val cellMaxWidth = ((maxWidth - spacing * (columns - 1)) / columns).coerceAtLeast(1.dp)
        val cellMaxHeight = ((maxHeight - spacing * (rows - 1)) / rows).coerceAtLeast(1.dp)

        FlowRow(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
            maxItemsInEachRow = columns,
        ) {
            state.images.forEachIndexed { index, image ->
                GeneratedImageTile(
                    image = image,
                    index = index,
                    maxWidth = cellMaxWidth,
                    maxHeight = cellMaxHeight,
                )
            }
        }
    }
}

@Composable
private fun GeneratedImageTile(
    image: androidx.compose.ui.graphics.ImageBitmap,
    index: Int,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    val imageWidth = image.width.coerceAtLeast(1).toFloat()
    val imageHeight = image.height.coerceAtLeast(1).toFloat()
    val aspectRatio = imageWidth / imageHeight
    val density = LocalDensity.current
    val naturalWidth = with(density) { image.width.toDp() }
    val naturalHeight = with(density) { image.height.toDp() }

    var displayWidth = naturalWidth.coerceAtMost(maxWidth)
    var displayHeight = displayWidth / aspectRatio
    if (displayHeight > maxHeight) {
        displayHeight = maxHeight
        displayWidth = maxHeight * aspectRatio
    }
    if (displayHeight > naturalHeight) {
        displayHeight = naturalHeight
        displayWidth = naturalHeight * aspectRatio
    }

    Box(
        modifier = Modifier
            .width(displayWidth)
            .height(displayHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = "Generated image ${index + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ProgressCard(state: GenerationUiState) {
    val progressFraction = if (state.progressSteps > 0) {
        (state.progressStep.toFloat() / state.progressSteps.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val eta = if (state.progressSteps > 0 && state.progressStep > 0 && state.progressTime > 0.0) {
        val averageStep = state.progressTime / state.progressStep
        ((state.progressSteps - state.progressStep) * averageStep).toInt()
    } else {
        0
    }

    Column(
        modifier = Modifier.widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.progressPhase.ifBlank { "Generating..." },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Generating image(s)...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalProgressBar(
            progress = progressFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Step ${state.progressStep} / ${state.progressSteps}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildString {
                    append("${"%.1f".format(state.progressTime)}s")
                    if (eta > 0) append("   Remaining: ~${eta}s")
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.progressMessage.isNotBlank()) {
            Text(
                text = state.progressMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionBar(
    state: GenerationUiState,
    backendState: BackendUiState,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onGenerate,
                enabled = backendState.status == BackendStatus.Ready && state.prompt.isNotBlank(),
                modifier = Modifier
                    .height(52.dp)
                    .width(240.dp),
            ) {
                ButtonContent(
                    icon = Icons.Default.PlayArrow,
                    text = when {
                        state.isGenerating && state.queueCount > 0 -> "Queue"
                        state.isGenerating -> "Generating..."
                        else -> "Generate"
                    },
                    suffix = if (state.queueCount > 0) "(${state.queueCount})" else null,
                )
            }

            IconButton(
                onClick = onToggleEndless,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (state.isEndless) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Endless generation",
                    tint = if (state.isEndless) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(onClick = onGoBack, enabled = state.canGoBack) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous generation")
                }
                Surface(
                    modifier = Modifier.height(44.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Box(
                        modifier = Modifier.width(84.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.history.isEmpty()) "0 / 0" else "${state.historyIndex + 1} / ${state.history.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                IconButton(onClick = onGoForward, enabled = state.canGoForward) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next generation")
                }
            }

            Text(
                text = when (backendState.status) {
                    BackendStatus.Ready -> "Worker ready"
                    BackendStatus.Starting -> "Worker starting"
                    BackendStatus.Error -> "Worker error"
                    BackendStatus.Stopped -> "Worker stopped"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (backendState.status == BackendStatus.Ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactFieldFrame(
        label = label,
        modifier = modifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CompactFieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(58.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
}

@Composable
private fun SubtleTextButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(5.dp)
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.45f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CompactDropdownField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactFieldFrame(
        label = label,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SamplerMenu(
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier.onGloballyPositioned { anchorSize = it.size }) {
        CompactDropdownField(
            label = "Sampler",
            value = value,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        AnchoredDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = options,
            anchorSize = anchorSize,
            minWidth = 180.dp,
            onSelect = { option ->
                onChange(option)
                expanded = false
            },
        )
    }
}

@Composable
private fun AspectRatioMenu(
    width: String,
    height: String,
    onApplyAspectRatio: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratios = listOf(
        "1:1" to (1 to 1),
        "4:3" to (4 to 3),
        "3:4" to (3 to 4),
        "3:2" to (3 to 2),
        "2:3" to (2 to 3),
        "16:9" to (16 to 9),
        "9:16" to (9 to 16),
        "16:10" to (16 to 10),
        "10:16" to (10 to 16),
        "21:9" to (21 to 9),
    )
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier.onGloballyPositioned { anchorSize = it.size }) {
        CompactDropdownField(
            label = "AR",
            value = aspectRatioLabel(width, height),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        AnchoredDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = ratios.map { it.first },
            anchorSize = anchorSize,
            minWidth = 120.dp,
            onSelect = { selected ->
                val ratio = ratios.first { it.first == selected }.second
                onApplyAspectRatio(ratio.first, ratio.second)
                expanded = false
            },
        )
    }
}

@Composable
private fun AnchoredDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<String>,
    anchorSize: IntSize,
    minWidth: Dp,
    onSelect: (String) -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val menuWidth = with(density) {
        if (anchorSize.width > 0) anchorSize.width.toDp() else minWidth
    }
    val gapPx = with(density) { 4.dp.roundToPx() }

    Popup(
        popupPositionProvider = DropdownPositionProvider(gapPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(menuWidth),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                options.forEach { option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private class DropdownPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val belowY = anchorBounds.bottom + gapPx
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val y = when {
            belowY + popupContentSize.height <= windowSize.height -> belowY
            aboveY >= 0 -> aboveY
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        val x = anchorBounds.left.coerceIn(
            minimumValue = 0,
            maximumValue = (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )

        return IntOffset(x, y)
    }
}

private fun aspectRatioLabel(widthValue: String, heightValue: String): String {
    val width = widthValue.toIntOrNull()
    val height = heightValue.toIntOrNull()
    if (width == null || height == null || width <= 0 || height <= 0) return "-"

    val divisor = gcd(width, height).coerceAtLeast(1)
    return "${width / divisor}:${height / divisor}"
}

@Composable
private fun PaddedTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ResolutionSlider(
    state: GenerationUiState,
    onScaleResolution: (Int) -> Unit,
) {
    val width = state.width.toIntOrNull() ?: 0
    val height = state.height.toIntOrNull() ?: 0
    val widthUnits = (width / 16).coerceAtLeast(1)
    val heightUnits = (height / 16).coerceAtLeast(1)
    val divisor = gcd(widthUnits, heightUnits).coerceAtLeast(1)
    val ratioWidthUnits = widthUnits / divisor
    val ratioHeightUnits = heightUnits / divisor
    val minMultiplier = maxOf(
        1,
        kotlin.math.ceil(64.0 / (ratioWidthUnits * 16)).toInt(),
        kotlin.math.ceil(64.0 / (ratioHeightUnits * 16)).toInt(),
    )
    val maxMultiplier = minOf(
        4096 / (ratioWidthUnits * 16),
        4096 / (ratioHeightUnits * 16),
    ).coerceAtLeast(minMultiplier)
    val currentMultiplier = divisor.coerceIn(minMultiplier, maxMultiplier)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("Overall Size Adjustment")
            Text(
                text = "$width x $height",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = currentMultiplier.toFloat(),
            onValueChange = { onScaleResolution(it.roundToInt()) },
            valueRange = minMultiplier.toFloat()..maxMultiplier.toFloat(),
            steps = 0,
        )
    }
}


@Composable
private fun ButtonContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    suffix: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
        suffix?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, fontWeight = FontWeight.Bold)
        }
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
