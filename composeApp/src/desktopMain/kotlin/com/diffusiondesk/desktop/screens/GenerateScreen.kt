package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
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
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.viewmodel.GenerationProgressStage
import com.diffusiondesk.desktop.viewmodel.GenerationStatus
import com.diffusiondesk.desktop.viewmodel.GenerationUiState
import com.diffusiondesk.desktop.viewmodel.IDEOGRAM_BBOX_GRID
import com.diffusiondesk.desktop.viewmodel.IdeogramStructureTab
import com.diffusiondesk.desktop.viewmodel.ideogramElementPreviews
import java.awt.Cursor
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
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
    onPromptCommit: () -> Unit,
    onUndoPrompt: () -> Unit,
    onRedoPrompt: () -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onStructuredTabSelected: (IdeogramStructureTab) -> Unit,
    onGenerateStructuredJson: () -> Unit,
    onStructuredJsonPromptChange: (String) -> Unit,
    onFormatStructuredJson: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
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
    onEnhancePrompt: () -> Unit,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onLeftPanelWidthChange: (Int) -> Unit,
    actionBarPosition: String,
    outputDir: String,
) {
    val showActionBarOnTop = actionBarPosition == "top"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (showActionBarOnTop) {
            ActionBar(
                state = state,
                backendState = backendState,
                isTop = true,
                generateEnabled = backendState.status == BackendStatus.Ready && generationPromptReady(state),
                onGenerate = onGenerate,
                onToggleEndless = onToggleEndless,
                onPresetSelected = onPresetSelected,
                onGoBack = onGoBack,
                onGoForward = onGoForward,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(DeskScreenPadding),
        ) {
            val minPanelWidth = 480.dp
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
                    onPromptCommit = onPromptCommit,
                    onUndoPrompt = onUndoPrompt,
                    onRedoPrompt = onRedoPrompt,
                    onNegativePromptChange = onNegativePromptChange,
                    onStructuredTabSelected = onStructuredTabSelected,
                    onGenerateStructuredJson = onGenerateStructuredJson,
                    onStructuredJsonPromptChange = onStructuredJsonPromptChange,
                    onFormatStructuredJson = onFormatStructuredJson,
                    onCompositionBboxChange = onCompositionBboxChange,
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
                    onEnhancePrompt = onEnhancePrompt,
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight(),
                )

                Splitter(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(DeskLayoutGap)
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
                    outputDir = outputDir,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        if (!showActionBarOnTop) {
            ActionBar(
                state = state,
                backendState = backendState,
                isTop = false,
                generateEnabled = backendState.status == BackendStatus.Ready && generationPromptReady(state),
                onGenerate = onGenerate,
                onToggleEndless = onToggleEndless,
                onPresetSelected = onPresetSelected,
                onGoBack = onGoBack,
                onGoForward = onGoForward,
            )
        }
    }
}

@Composable
private fun Splitter(
    modifier: Modifier = Modifier,
) {
    val cursorIcon = remember {
        PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
    }
    Box(
        modifier = modifier.pointerHoverIcon(cursorIcon),
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
    onPromptCommit: () -> Unit,
    onUndoPrompt: () -> Unit,
    onRedoPrompt: () -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onStructuredTabSelected: (IdeogramStructureTab) -> Unit,
    onGenerateStructuredJson: () -> Unit,
    onStructuredJsonPromptChange: (String) -> Unit,
    onFormatStructuredJson: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
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
    onEnhancePrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showNegativePrompt = (state.cfgScale.toDoubleOrNull() ?: 0.0) > 1.0

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
    ) {
        PromptTabsHeader(
            state = state,
            onStructuredTabSelected = onStructuredTabSelected,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(DeskPanelCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(DeskPanelPadding),
                verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
            ) {
                PromptTabContent(
                    state = state,
                    showNegativePrompt = showNegativePrompt,
                    onPromptChange = onPromptChange,
                    onPromptCommit = onPromptCommit,
                    onUndoPrompt = onUndoPrompt,
                    onRedoPrompt = onRedoPrompt,
                    onNegativePromptChange = onNegativePromptChange,
                    onGenerateStructuredJson = onGenerateStructuredJson,
                    onStructuredJsonPromptChange = onStructuredJsonPromptChange,
                    onFormatStructuredJson = onFormatStructuredJson,
                    onCompositionBboxChange = onCompositionBboxChange,
                    onEnhancePrompt = onEnhancePrompt,
                )

                if (state.ideogram.selectedTab == IdeogramStructureTab.Text) {
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
                    GenerationParameterControls(
                        state = state,
                        samplerOptions = samplerOptions,
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
                        showReset = false,
                        onResetToPresetDefaults = onResetToPresetDefaults,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptTabsHeader(
    state: GenerationUiState,
    onStructuredTabSelected: (IdeogramStructureTab) -> Unit,
) {
    DeskTabHeader(
        tabs = listOf(
            DeskTabItem(
                selected = state.ideogram.selectedTab == IdeogramStructureTab.Text,
                icon = Icons.AutoMirrored.Filled.Article,
                label = "Text",
                onClick = { onStructuredTabSelected(IdeogramStructureTab.Text) },
            ),
            DeskTabItem(
                selected = state.ideogram.selectedTab == IdeogramStructureTab.Json,
                icon = Icons.Default.Code,
                label = "JSON",
                onClick = { onStructuredTabSelected(IdeogramStructureTab.Json) },
            ),
            DeskTabItem(
                selected = state.ideogram.selectedTab == IdeogramStructureTab.Preview,
                icon = Icons.Default.Dashboard,
                label = "Composition",
                onClick = { onStructuredTabSelected(IdeogramStructureTab.Preview) },
            ),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PromptTabContent(
    state: GenerationUiState,
    showNegativePrompt: Boolean,
    onPromptChange: (String) -> Unit,
    onPromptCommit: () -> Unit,
    onUndoPrompt: () -> Unit,
    onRedoPrompt: () -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onGenerateStructuredJson: () -> Unit,
    onStructuredJsonPromptChange: (String) -> Unit,
    onFormatStructuredJson: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
    onEnhancePrompt: () -> Unit,
) {
    when (state.ideogram.selectedTab) {
        IdeogramStructureTab.Text -> TextPromptPanel(
            state = state,
            showNegativePrompt = showNegativePrompt,
            onPromptChange = onPromptChange,
            onPromptCommit = onPromptCommit,
            onUndoPrompt = onUndoPrompt,
            onRedoPrompt = onRedoPrompt,
            onNegativePromptChange = onNegativePromptChange,
            onEnhancePrompt = onEnhancePrompt,
        )
        IdeogramStructureTab.Json -> JsonPromptPanel(
            state = state,
            onGenerateStructuredJson = onGenerateStructuredJson,
            onStructuredJsonPromptChange = onStructuredJsonPromptChange,
            onFormatStructuredJson = onFormatStructuredJson,
        )
        IdeogramStructureTab.Preview -> {
            StructuredJsonStatus(state)
            IdeogramLayoutPreview(
                jsonPrompt = state.ideogram.jsonPrompt,
                width = state.width.toIntOrNull() ?: 1024,
                height = state.height.toIntOrNull() ?: 1024,
                onElementBboxChange = onCompositionBboxChange,
                modifier = Modifier.heightIn(min = 300.dp),
            )
        }
    }
}

@Composable
private fun TextPromptPanel(
    state: GenerationUiState,
    showNegativePrompt: Boolean,
    onPromptChange: (String) -> Unit,
    onPromptCommit: () -> Unit,
    onUndoPrompt: () -> Unit,
    onRedoPrompt: () -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onEnhancePrompt: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Prompt")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SubtleTextButton(
                    icon = Icons.Default.AutoFixHigh,
                    text = if (state.isEnhancingPrompt) "Enhancing..." else "Enhance",
                    onClick = onEnhancePrompt,
                    enabled = state.prompt.isNotBlank() && !state.isEnhancingPrompt,
                )
                PromptHistoryButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Previous prompt",
                    onClick = onUndoPrompt,
                    enabled = state.canUndoPrompt,
                )
                PromptHistoryButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Next prompt",
                    onClick = onRedoPrompt,
                    enabled = state.canRedoPrompt,
                )
            }
        }
        PaddedTextArea(
            value = state.prompt,
            onValueChange = onPromptChange,
            onFocusLost = onPromptCommit,
            enabled = !state.isEnhancingPrompt,
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
    }
}

@Composable
private fun JsonPromptPanel(
    state: GenerationUiState,
    onGenerateStructuredJson: () -> Unit,
    onStructuredJsonPromptChange: (String) -> Unit,
    onFormatStructuredJson: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onGenerateStructuredJson,
                enabled = state.prompt.isNotBlank() && !state.ideogram.isGeneratingJson,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                ButtonContent(
                    icon = Icons.Default.AutoFixHigh,
                    text = if (state.ideogram.isGeneratingJson) "Generating JSON..." else "Generate JSON",
                )
            }
            DeskIconButton(Icons.Default.CheckCircle, "Format JSON", onFormatStructuredJson, tooltip = "Format JSON")
        }

        if (!state.ideogram.isGeneratingJson || state.ideogram.jsonError != null) {
            StructuredJsonStatus(state)
        }
        PaddedTextArea(
            value = state.ideogram.jsonPrompt,
            onValueChange = onStructuredJsonPromptChange,
            enabled = !state.ideogram.isGeneratingJson,
            monospace = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp),
        )
    }
}

@Composable
private fun StructuredJsonStatus(state: GenerationUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (state.ideogram.jsonError == null) Color(0xFF2EAD4A) else MaterialTheme.colorScheme.error, RoundedCornerShape(50)),
        )
        Text(
            text = state.ideogram.jsonError ?: state.ideogram.jsonStatus,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.ideogram.jsonError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IdeogramLayoutPreview(
    jsonPrompt: String,
    width: Int,
    height: Int,
    onElementBboxChange: (Int, List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val elements = ideogramElementPreviews(jsonPrompt)
    val density = LocalDensity.current
    val latestElements by rememberUpdatedState(elements)
    val latestOnElementBboxChange by rememberUpdatedState(onElementBboxChange)
    var selectedIndex by remember { mutableStateOf(0) }
    LaunchedEffect(elements.size) {
        if (elements.isEmpty()) {
            selectedIndex = 0
        } else if (selectedIndex !in elements.indices) {
            selectedIndex = elements.lastIndex
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 340.dp)
                .clip(RoundedCornerShape(DeskControlCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DeskControlCornerRadius)),
        ) {
            val aspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1).toFloat()
            val heightAtFullWidth = maxWidth / aspect
            val canvasWidth: Dp
            val canvasHeight: Dp
            if (heightAtFullWidth <= maxHeight) {
                canvasWidth = maxWidth
                canvasHeight = heightAtFullWidth
            } else {
                canvasHeight = maxHeight
                canvasWidth = (canvasHeight * aspect).coerceAtMost(maxWidth)
            }
            val canvasWidthPx = with(density) { canvasWidth.toPx() }.coerceAtLeast(1f)
            val canvasHeightPx = with(density) { canvasHeight.toPx() }.coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(canvasHeight)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                elements.forEachIndexed { index, element ->
                    val bbox = normalizeCompositionBbox(element.bbox)
                    if (bbox != null) {
                        val top = canvasHeight * (bbox[0] / 1000f)
                        val left = canvasWidth * (bbox[1] / 1000f)
                        val bottom = canvasHeight * (bbox[2] / 1000f)
                        val right = canvasWidth * (bbox[3] / 1000f)
                        val boxColor = if (element.type == "text") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .offset(left, top)
                                .width((right - left).coerceAtLeast(8.dp))
                                .height((bottom - top).coerceAtLeast(8.dp))
                                .border(if (isSelected) 3.dp else 2.dp, boxColor, RoundedCornerShape(2.dp))
                                .background(boxColor.copy(alpha = 0.10f))
                                .pointerInput(index, canvasWidthPx, canvasHeightPx) {
                                    var startBbox = emptyList<Int>()
                                    var dragX = 0f
                                    var dragY = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            selectedIndex = index
                                            startBbox = latestElements.getOrNull(index)?.bbox.orEmpty()
                                            dragX = 0f
                                            dragY = 0f
                                        },
                                        onDrag = { _, dragAmount ->
                                            dragX += dragAmount.x
                                            dragY += dragAmount.y
                                            val deltaX = ((dragX / canvasWidthPx) * 1000f).roundToInt()
                                            val deltaY = ((dragY / canvasHeightPx) * 1000f).roundToInt()
                                            moveCompositionBbox(startBbox, deltaX, deltaY)?.let { nextBbox ->
                                                latestOnElementBboxChange(index, nextBbox)
                                            }
                                        },
                                    )
                                }
                                .padding(4.dp),
                        ) {
                            Text(
                                text = if (element.type == "text" && element.text.isNotBlank()) element.text else "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = boxColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSelected) {
                                CompositionResizeHandle.values().forEach { handle ->
                                    CompositionResizeHandleBox(
                                        handle = handle,
                                        color = boxColor,
                                        startBbox = { latestElements.getOrNull(index)?.bbox.orEmpty() },
                                        onDragStart = { selectedIndex = index },
                                        onDrag = { startBbox, deltaX, deltaY ->
                                            val deltaNormX = ((deltaX / canvasWidthPx) * 1000f).roundToInt()
                                            val deltaNormY = ((deltaY / canvasHeightPx) * 1000f).roundToInt()
                                            resizeCompositionBbox(startBbox, deltaNormX, deltaNormY, handle)?.let { nextBbox ->
                                                latestOnElementBboxChange(index, nextBbox)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (elements.isEmpty()) {
            Text("No valid elements to preview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            elements.forEachIndexed { index, element ->
                ElementPreviewRow(
                    index = index + 1,
                    type = element.type,
                    textValue = element.text,
                    desc = element.desc,
                    selected = index == selectedIndex,
                    onClick = { selectedIndex = index },
                )
            }
        }
    }
}

private enum class CompositionResizeHandle {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

@Composable
private fun BoxScope.CompositionResizeHandleBox(
    handle: CompositionResizeHandle,
    color: Color,
    startBbox: () -> List<Int>,
    onDragStart: () -> Unit,
    onDrag: (List<Int>, Float, Float) -> Unit,
) {
    val alignment = when (handle) {
        CompositionResizeHandle.TopLeft -> Alignment.TopStart
        CompositionResizeHandle.TopRight -> Alignment.TopEnd
        CompositionResizeHandle.BottomLeft -> Alignment.BottomStart
        CompositionResizeHandle.BottomRight -> Alignment.BottomEnd
    }
    Box(
        modifier = Modifier
            .align(alignment)
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, color, RoundedCornerShape(2.dp))
            .pointerInput(handle) {
                var capturedBbox = emptyList<Int>()
                var dragX = 0f
                var dragY = 0f
                detectDragGestures(
                    onDragStart = {
                        onDragStart()
                        capturedBbox = startBbox()
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { _, dragAmount ->
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        onDrag(capturedBbox, dragX, dragY)
                    },
                )
            },
    )
}

private fun normalizeCompositionBbox(values: List<Int>): List<Int>? {
    if (values.size != 4) return null
    val yMin = snapCompositionCoord(values[0])
    val xMin = snapCompositionCoord(values[1])
    val yMax = snapCompositionCoord(values[2])
    val xMax = snapCompositionCoord(values[3])
    val minSize = IDEOGRAM_BBOX_GRID
    return listOf(
        yMin.coerceIn(0, (yMax - minSize).coerceAtLeast(0)),
        xMin.coerceIn(0, (xMax - minSize).coerceAtLeast(0)),
        yMax.coerceIn((yMin + minSize).coerceAtMost(1000), 1000),
        xMax.coerceIn((xMin + minSize).coerceAtMost(1000), 1000),
    )
}

private fun moveCompositionBbox(values: List<Int>, deltaX: Int, deltaY: Int): List<Int>? {
    val bbox = normalizeCompositionBbox(values) ?: return null
    val boxHeight = (bbox[2] - bbox[0]).coerceAtLeast(IDEOGRAM_BBOX_GRID)
    val boxWidth = (bbox[3] - bbox[1]).coerceAtLeast(IDEOGRAM_BBOX_GRID)
    val nextYMin = snapCompositionCoord(bbox[0] + deltaY).coerceIn(0, 1000 - boxHeight)
    val nextXMin = snapCompositionCoord(bbox[1] + deltaX).coerceIn(0, 1000 - boxWidth)
    return listOf(nextYMin, nextXMin, nextYMin + boxHeight, nextXMin + boxWidth)
}

private fun resizeCompositionBbox(values: List<Int>, deltaX: Int, deltaY: Int, handle: CompositionResizeHandle): List<Int>? {
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
            xMax = snapCompositionCoord(bbox[3] + deltaX).coerceIn(xMin + minSize, 1000)
        }
        CompositionResizeHandle.BottomLeft -> {
            yMax = snapCompositionCoord(bbox[2] + deltaY).coerceIn(yMin + minSize, 1000)
            xMin = snapCompositionCoord(bbox[1] + deltaX).coerceIn(0, xMax - minSize)
        }
        CompositionResizeHandle.BottomRight -> {
            yMax = snapCompositionCoord(bbox[2] + deltaY).coerceIn(yMin + minSize, 1000)
            xMax = snapCompositionCoord(bbox[3] + deltaX).coerceIn(xMin + minSize, 1000)
        }
    }
    return listOf(yMin, xMin, yMax, xMax)
}

private fun snapCompositionCoord(value: Int): Int =
    ((value.toFloat() / IDEOGRAM_BBOX_GRID).roundToInt() * IDEOGRAM_BBOX_GRID).coerceIn(0, 1000)

@Composable
private fun ElementPreviewRow(
    index: Int,
    type: String,
    textValue: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(DeskControlCornerRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape,
            )
            .padding(DeskControlSpacing),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = if (type == "text" && textValue.isNotBlank()) "$index. text: $textValue" else "$index. $type",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = desc.ifBlank { "No description." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun generationPromptReady(state: GenerationUiState): Boolean {
    val selectedPreset = state.presets.firstOrNull { it.id == state.selectedPresetId }
    return when (selectedPreset?.promptMode ?: ImagePromptMode.Text) {
        ImagePromptMode.Text -> state.prompt.isNotBlank()
        ImagePromptMode.Json -> state.ideogram.jsonPrompt.isNotBlank() && state.ideogram.isJsonValid
    }
}

@Composable
internal fun GenerationParameterControls(
    state: GenerationUiState,
    samplerOptions: List<String>,
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
    showReset: Boolean = true,
    onResetToPresetDefaults: () -> Unit,
) {
    if (showReset) {
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
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactNumberField("Steps", state.steps, onStepsChange, Modifier.weight(0.8f).widthIn(min = 104.dp), step = 1.0, minValue = 1.0)
        CompactNumberField("Batch", state.batchCount, onBatchCountChange, Modifier.weight(0.8f).widthIn(min = 104.dp), step = 1.0, minValue = 1.0)
        CompactNumberField("Seed", state.seed, onSeedChange, Modifier.weight(1.1f).widthIn(min = 126.dp), step = 1.0)
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
        CompactNumberField("Width", state.width, onWidthChange, Modifier.weight(1f).widthIn(min = 126.dp), step = 16.0, minValue = 64.0)
        CompactIconButton(
            icon = Icons.Default.SwapHoriz,
            contentDescription = "Swap dimensions",
            onClick = onSwapDimensions,
        )
        CompactNumberField("Height", state.height, onHeightChange, Modifier.weight(1f).widthIn(min = 126.dp), step = 16.0, minValue = 64.0)
        AspectRatioMenu(
            width = state.width,
            height = state.height,
            onApplyAspectRatio = onApplyAspectRatio,
            modifier = Modifier.weight(0.78f).widthIn(min = 92.dp),
        )
    }
    ResolutionSlider(
        state = state,
        onScaleResolution = onScaleResolution,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactNumberField("CFG", state.cfgScale, onCfgScaleChange, Modifier.weight(1f).widthIn(min = 118.dp), step = 0.1, minValue = 0.0, decimalPlaces = 1)
        SamplerMenu(
            value = state.sampler,
            options = samplerOptions,
            onChange = onSamplerChange,
            modifier = Modifier.weight(1f).widthIn(min = 160.dp),
        )
    }
}

@Composable
internal fun PreviewPanel(
    state: GenerationUiState,
    outputDir: String,
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
                GeneratedImageGrid(state, outputDir)
            }
            state.currentHistoryItem?.status == GenerationStatus.Pending -> {
                Text("Queued", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratedImageGrid(
    state: GenerationUiState,
    outputDir: String,
) {
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
                    outputDir = outputDir,
                )
            }
        }
    }
}

@Composable
private fun GeneratedImageTile(
    image: GeneratedImage,
    index: Int,
    maxWidth: Dp,
    maxHeight: Dp,
    outputDir: String,
) {
    val bitmap = image.bitmap
    val imageWidth = bitmap.width.coerceAtLeast(1).toFloat()
    val imageHeight = bitmap.height.coerceAtLeast(1).toFloat()
    val aspectRatio = imageWidth / imageHeight
    val density = LocalDensity.current
    val naturalWidth = with(density) { bitmap.width.toDp() }
    val naturalHeight = with(density) { bitmap.height.toDp() }
    val localFile = remember(image.sourceUrl, outputDir) {
        image.resolveOutputFile(outputDir)
    }

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

    ContextMenuArea(
        items = {
            buildList {
                add(ContextMenuItem("Copy Image") { image.copyToClipboard() })
                add(ContextMenuItem("Save Image As...") { image.saveAs(index) })
                if (localFile != null && localFile.exists()) {
                    add(ContextMenuItem("Open Image") { openFile(localFile) })
                    add(ContextMenuItem("Show in Explorer") { showInExplorer(localFile) })
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .width(displayWidth)
                .height(displayHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Generated image ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun GeneratedImage.copyToClipboard() {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(bufferedImage), null)
}

private fun GeneratedImage.saveAs(index: Int) {
    val extension = imageExtension().ifBlank { "png" }
    val dialog = FileDialog(activeFrame(), "Save Image", FileDialog.SAVE).apply {
        file = sourceFileName() ?: "generated-image-${index + 1}.$extension"
        isVisible = true
    }
    val selectedFile = dialog.file ?: return
    val directory = dialog.directory ?: return
    val target = File(directory, selectedFile).withImageExtension(extension)
    target.writeBytes(bytes)
}

private fun GeneratedImage.resolveOutputFile(outputDir: String): File? {
    if (outputDir.isBlank()) return null
    val path = runCatching { URI(sourceUrl).path }.getOrNull() ?: return null
    val marker = "/outputs/"
    val markerIndex = path.indexOf(marker)
    if (markerIndex < 0) return null

    val relativePath = URLDecoder.decode(
        path.substring(markerIndex + marker.length),
        StandardCharsets.UTF_8,
    )
    val outputRoot = File(outputDir).canonicalFile
    val file = File(outputRoot, relativePath.replace('/', File.separatorChar)).canonicalFile
    return file.takeIf {
        it.path == outputRoot.path || it.path.startsWith(outputRoot.path + File.separator)
    }
}

private fun GeneratedImage.sourceFileName(): String? {
    val path = runCatching { URI(sourceUrl).path }.getOrNull() ?: return null
    return URLDecoder.decode(path.substringAfterLast('/'), StandardCharsets.UTF_8)
        .takeIf { it.isNotBlank() }
}

private fun GeneratedImage.imageExtension(): String {
    return sourceFileName()
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.US)
        ?.takeIf { it in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") }
        ?: "png"
}

private fun File.withImageExtension(extension: String): File {
    return if (name.contains('.')) this else File(parentFile, "$name.$extension")
}

private fun formatProgressDuration(seconds: Double): String {
    if (seconds < 60.0) {
        return "${"%.1f".format(Locale.US, seconds.coerceAtLeast(0.0))}s"
    }

    val roundedSeconds = seconds.roundToInt().coerceAtLeast(0)
    val minutes = roundedSeconds / 60
    val remainingSeconds = roundedSeconds % 60
    return "${minutes}m ${remainingSeconds.toString().padStart(2, '0')}s"
}

private fun activeFrame(): Frame? {
    return Frame.getFrames().firstOrNull { it.isActive }
        ?: Frame.getFrames().firstOrNull { it.isVisible }
}

private fun openFile(file: File) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file)
    }
}

private fun showInExplorer(file: File) {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
    } else if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file.parentFile)
    }
}

private class ImageTransferable(
    private val image: java.awt.Image,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) {
            throw UnsupportedOperationException("Unsupported clipboard flavor: $flavor")
        }
        return image
    }
}

@Composable
private fun ProgressCard(state: GenerationUiState) {
    Column(
        modifier = Modifier.widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
    ) {
        Text(
            text = state.progressPhase.ifBlank { "Generating..." },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Generating image(s)...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProgressStageList(state.progressStages)
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
                    append(formatProgressDuration(state.progressTime))
                    if (state.progressEtaSeconds > 0) {
                        append("   Remaining: ~")
                        append(formatProgressDuration(state.progressEtaSeconds.toDouble()))
                    }
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
private fun ProgressStageList(stages: List<GenerationProgressStage>) {
    val visibleStages = stages.ifEmpty {
        listOf(GenerationProgressStage("starting", "Prepare", 0.25f, isActive = true, isComplete = false))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleStages.forEach { stage ->
            ProgressStageRow(stage)
        }
    }
}

@Composable
private fun ProgressStageRow(stage: GenerationProgressStage) {
    val labelColor = when {
        stage.isComplete -> MaterialTheme.colorScheme.primary
        stage.isActive -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when {
        stage.isComplete -> "Done"
        stage.isActive -> "Running"
        else -> "Waiting"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stage.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (stage.isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
            )
        }
        HorizontalProgressBar(
            progress = stage.progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

@Composable
internal fun ActionBar(
    state: GenerationUiState,
    backendState: BackendUiState,
    isTop: Boolean,
    generateEnabled: Boolean,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(
                start = DeskScreenPadding,
                top = if (isTop) DeskScreenPadding else 0.dp,
                end = DeskScreenPadding,
                bottom = if (isTop) 0.dp else DeskScreenPadding,
            ),
        shape = RoundedCornerShape(DeskPanelCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val compact = maxWidth < 1120.dp
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DeskGroupSpacing),
            ) {
                Button(
                    onClick = onGenerate,
                    enabled = generateEnabled,
                    modifier = Modifier
                        .height(52.dp)
                        .width(if (compact) 200.dp else 240.dp),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    HistoryNavButton(
                        icon = Icons.Default.ChevronLeft,
                        contentDescription = "Previous generation",
                        onClick = onGoBack,
                        enabled = state.canGoBack,
                    )
                    Surface(
                        modifier = Modifier.height(44.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha),
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
                    HistoryNavButton(
                        icon = Icons.Default.ChevronRight,
                        contentDescription = "Next generation",
                        onClick = onGoForward,
                        enabled = state.canGoForward,
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

                PresetActionControl(
                    state = state,
                    backendState = backendState,
                    onPresetSelected = onPresetSelected,
                    modifier = Modifier.width(if (compact) 220.dp else 300.dp),
                )

                Spacer(Modifier.weight(1f))

                if (!compact) {
                    ActionStatus(
                        state = state,
                        backendState = backendState,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetActionControl(
    state: GenerationUiState,
    backendState: BackendUiState,
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPreset = state.presets.firstOrNull { it.id == state.selectedPresetId }
    val dotColor = when {
        state.presetLoadFailed -> MaterialTheme.colorScheme.error
        state.isLoadingPreset || state.isLoadingPresets -> Color(0xFFFFA000)
        backendState.status == BackendStatus.Ready && selectedPreset?.id == state.loadedPresetId -> Color(0xFF2EAD4A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .height(44.dp)
            .onGloballyPositioned { anchorSize = it.size },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = state.presets.isNotEmpty()) { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(dotColor, CircleShape),
                )
                Text(
                    text = selectedPreset?.name ?: "No preset",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectedPreset != null) {
                    Text(
                        text = selectedPreset.promptMode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnchoredDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = state.presets.map { it.name },
            anchorSize = anchorSize,
            minWidth = 220.dp,
            onSelect = { name ->
                state.presets.firstOrNull { it.name == name }?.let { preset ->
                    onPresetSelected(preset.id)
                }
                expanded = false
            },
        )
    }
}

@Composable
private fun HistoryNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.45f else 0.25f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ActionStatus(
    state: GenerationUiState,
    backendState: BackendUiState,
) {
    val item = state.currentHistoryItem
    val generationStatus = item?.status
    val statusText = when (generationStatus) {
        GenerationStatus.Pending -> "Queued"
        GenerationStatus.Processing -> "Generating"
        GenerationStatus.Completed -> "Completed"
        GenerationStatus.Failed -> "Failed"
        null -> "No generation selected"
    }
    val statusColor = when (generationStatus) {
        GenerationStatus.Completed -> MaterialTheme.colorScheme.primary
        GenerationStatus.Failed -> MaterialTheme.colorScheme.error
        GenerationStatus.Pending,
        GenerationStatus.Processing,
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val workerText = when (backendState.status) {
        BackendStatus.Ready -> "Worker ready"
        BackendStatus.Starting -> "Worker starting"
        BackendStatus.Error -> "Worker error"
        BackendStatus.Stopped -> "Worker stopped"
    }
    val workerColor = if (backendState.status == BackendStatus.Ready) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val detailText = when {
        state.error != null -> state.error
        item?.generationTime != null -> "Generated in ${"%.1f".format(item.generationTime)}s"
        state.message.isNotBlank() -> state.message
        else -> workerText
    }
    val detailColor = when {
        state.error != null -> MaterialTheme.colorScheme.error
        detailText == workerText -> workerColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.widthIn(min = 180.dp, max = 360.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detailText,
            style = MaterialTheme.typography.bodySmall,
            color = detailColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun CompactNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Double,
    minValue: Double? = null,
    maxValue: Double? = null,
    decimalPlaces: Int = 0,
) {
    CompactFieldFrame(
        label = label,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 30.dp)
                    .padding(end = 3.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
            NumberStepper(
                onIncrement = {
                    onChange(stepNumberValue(value, step, minValue, maxValue, decimalPlaces))
                },
                onDecrement = {
                    onChange(stepNumberValue(value, -step, minValue, maxValue, decimalPlaces))
                },
            )
        }
    }
}

@Composable
private fun NumberStepper(
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight(),
    ) {
        NumberStepperButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Increase value",
            onClick = onIncrement,
            modifier = Modifier.weight(1f),
        )
        NumberStepperButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Decrease value",
            onClick = onDecrement,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberStepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun stepNumberValue(
    value: String,
    delta: Double,
    minValue: Double?,
    maxValue: Double?,
    decimalPlaces: Int,
): String {
    val current = value.toDoubleOrNull() ?: 0.0
    val stepped = (current + delta)
        .let { if (minValue == null) it else it.coerceAtLeast(minValue) }
        .let { if (maxValue == null) it else it.coerceAtMost(maxValue) }

    return if (decimalPlaces > 0) {
        "%.${decimalPlaces}f".format(Locale.US, stepped)
    } else {
        stepped.roundToInt().toString()
    }
}

@Composable
private fun CompactFieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    labelMinWidth: Dp = 50.dp,
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
                .widthIn(min = labelMinWidth)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
}

@Composable
internal fun SubtleTextButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(5.dp)
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
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
private fun PromptHistoryButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(5.dp)
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 24.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.75f else 0.35f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp),
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
    onFocusLost: (() -> Unit)? = null,
    enabled: Boolean = true,
    monospace: Boolean = false,
) {
    val shape = RoundedCornerShape(5.dp)
    var hadFocus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (hadFocus && !focusState.isFocused) {
                    onFocusLost?.invoke()
                }
                hadFocus = focusState.isFocused
            }
            .clip(shape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        ),
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
