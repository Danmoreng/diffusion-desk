package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    .weight(1f)
                    .fillMaxHeight(),
            )

            PreviewPanel(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
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

            Label("Negative Prompt")
            PaddedTextArea(
                value = state.negativePrompt,
                onValueChange = onNegativePromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 74.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("Parameters")
                Text(
                    text = "Reset to Preset Defaults",
                    modifier = Modifier.clickable(onClick = onResetToPresetDefaults),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField("Steps", state.steps, onStepsChange, Modifier.weight(0.8f))
                CompactTextField("Batch", state.batchCount, onBatchCountChange, Modifier.weight(0.8f))
                CompactTextField("Seed", state.seed, onSeedChange, Modifier.weight(1.1f))
                IconButton(onClick = onRandomizeSeed) {
                    Icon(Icons.Default.Casino, contentDescription = "Random seed")
                }
                IconButton(
                    onClick = onReuseLastSeed,
                    enabled = state.history.any { it.usedSeed != null },
                ) {
                    Icon(Icons.Default.Recycling, contentDescription = "Reuse last seed")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField("Width", state.width, onWidthChange, Modifier.weight(1f))
                IconButton(onClick = onSwapDimensions) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap dimensions")
                }
                CompactTextField("Height", state.height, onHeightChange, Modifier.weight(1f))
                AspectRatioMenu(
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
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
                else -> {
                    Text("No generated image yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    Box(modifier = modifier) {
        CompactDropdownField(
            label = "Sampler",
            value = value,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AspectRatioMenu(
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
    Box(modifier = modifier) {
        CompactDropdownField(
            label = "AR",
            value = "Aspect Ratio",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ratios.forEach { (label, ratio) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onApplyAspectRatio(ratio.first, ratio.second)
                        expanded = false
                    },
                )
            }
        }
    }
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
