package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.core.LlmDebugEntry
import com.diffusiondesk.desktop.composition.CaptureImageMode
import com.diffusiondesk.desktop.composition.CompositionAction
import com.diffusiondesk.desktop.composition.PaletteTarget
import com.diffusiondesk.desktop.composition.StagedIdeogramStep
import com.diffusiondesk.desktop.viewmodel.GenerationUiState
import com.diffusiondesk.desktop.viewmodel.CompositionMutation
import com.diffusiondesk.desktop.viewmodel.CompositionImproveTarget
import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionElement
import com.diffusiondesk.desktop.viewmodel.IdeogramStyleField
import com.diffusiondesk.desktop.viewmodel.ideogramElementPreviews
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.jetbrains.jewel.ui.component.Checkbox
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
    onGenerateStructuredJson: () -> Unit,
    onRetryStagedJson: () -> Unit,
    onStartOverComposition: () -> Unit,
    onCompositionMutation: (CompositionMutation) -> Unit,
    onRunCompositionAction: (CompositionAction) -> Unit,
    onUndoComposition: () -> Unit,
    onRedoComposition: () -> Unit,
    onCompositionBboxEditStart: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
    onCompositionBboxEditEnd: () -> Unit,
    onCompositionBboxEditCancel: () -> Unit,
    onCompositionDescriptionChange: (Int, String) -> Unit,
    onCompositionTextChange: (Int, String) -> Unit,
    onCompositionPaletteChange: (Int, List<String>) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
    showCompositionOverlay: Boolean,
    onShowCompositionOverlayChange: (Boolean) -> Unit,
    onUseImageAsCompositionReferenceChange: (Boolean) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onLoraSearchChange: (String) -> Unit,
    onToggleLoraPanel: () -> Unit,
    onReloadLoras: () -> Unit,
    onToggleLora: (String) -> Unit,
    onLoraWeightChange: (String, Double) -> Unit,
    onRandomizeSeed: () -> Unit,
    onReuseLastSeed: () -> Unit,
    onSwapDimensions: () -> Unit,
    onApplyAspectRatio: (Int, Int) -> Unit,
    onScaleResolution: (Int) -> Unit,
    onResetToPresetDefaults: () -> Unit,
    onEnhancePrompt: () -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit,
    onToggleEndless: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onLeftPanelWidthChange: (Int) -> Unit,
    actionBarPosition: String,
    modelDir: String,
    outputDir: String,
    showLlmDebugConsole: Boolean,
    llmDebugEntries: List<LlmDebugEntry>,
    onClearLlmDebugLog: () -> Unit,
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
                generateEnabled = backendState.status == BackendStatus.Ready && generationPromptReady(state) && !state.ideogram.isGeneratingJson,
                onGenerate = onGenerate,
                onCancelGeneration = onCancelGeneration,
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
                    onGenerateStructuredJson = onGenerateStructuredJson,
                    onRetryStagedJson = onRetryStagedJson,
                    onStartOverComposition = onStartOverComposition,
                    onCompositionMutation = onCompositionMutation,
                    onRunCompositionAction = onRunCompositionAction,
                    onUndoComposition = onUndoComposition,
                    onRedoComposition = onRedoComposition,
                    onCompositionDescriptionChange = onCompositionDescriptionChange,
                    onCompositionTextChange = onCompositionTextChange,
                    onCompositionPaletteChange = onCompositionPaletteChange,
                    onCompositionElementSelected = onCompositionElementSelected,
                    onWidthChange = onWidthChange,
                    onHeightChange = onHeightChange,
                    onStepsChange = onStepsChange,
                    onCfgScaleChange = onCfgScaleChange,
                    onSeedChange = onSeedChange,
                    onSamplerChange = onSamplerChange,
                    onLoraSearchChange = onLoraSearchChange,
                    onToggleLoraPanel = onToggleLoraPanel,
                    onReloadLoras = onReloadLoras,
                    onToggleLora = onToggleLora,
                    onLoraWeightChange = onLoraWeightChange,
                    modelDir = modelDir,
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
                    showCompositionOverlay = showCompositionOverlay,
                    onShowCompositionOverlayChange = onShowCompositionOverlayChange,
                    onUseImageAsCompositionReferenceChange = onUseImageAsCompositionReferenceChange,
                    onCompositionElementSelected = onCompositionElementSelected,
                    onCompositionBboxEditStart = onCompositionBboxEditStart,
                    onCompositionBboxChange = onCompositionBboxChange,
                    onCompositionBboxEditEnd = onCompositionBboxEditEnd,
                    onCompositionBboxEditCancel = onCompositionBboxEditCancel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )

                if (showLlmDebugConsole) {
                    Splitter(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(DeskLayoutGap),
                    )

                    LlmDebugConsole(
                        entries = llmDebugEntries,
                        onClear = onClearLlmDebugLog,
                        modifier = Modifier
                            .width(430.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        if (!showActionBarOnTop) {
            ActionBar(
                state = state,
                backendState = backendState,
                isTop = false,
                generateEnabled = backendState.status == BackendStatus.Ready && generationPromptReady(state) && !state.ideogram.isGeneratingJson,
                onGenerate = onGenerate,
                onCancelGeneration = onCancelGeneration,
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
    onGenerateStructuredJson: () -> Unit,
    onRetryStagedJson: () -> Unit,
    onStartOverComposition: () -> Unit,
    onCompositionMutation: (CompositionMutation) -> Unit,
    onRunCompositionAction: (CompositionAction) -> Unit,
    onUndoComposition: () -> Unit,
    onRedoComposition: () -> Unit,
    onCompositionDescriptionChange: (Int, String) -> Unit,
    onCompositionTextChange: (Int, String) -> Unit,
    onCompositionPaletteChange: (Int, List<String>) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onLoraSearchChange: (String) -> Unit,
    onToggleLoraPanel: () -> Unit,
    onReloadLoras: () -> Unit,
    onToggleLora: (String) -> Unit,
    onLoraWeightChange: (String, Double) -> Unit,
    modelDir: String,
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
    val showCompositionControls = isJsonPromptPreset(state)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
    ) {
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
                    onEnhancePrompt = onEnhancePrompt,
                )

                LoraPanel(
                    state = state,
                    onSearchChange = onLoraSearchChange,
                    onToggleExpanded = onToggleLoraPanel,
                    onReload = onReloadLoras,
                    onToggleLora = onToggleLora,
                    onWeightChange = onLoraWeightChange,
                    modelDir = modelDir,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("Parameters")
                    DeskSubtleTextButton(
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
                    onSamplerChange = onSamplerChange,
                    onRandomizeSeed = onRandomizeSeed,
                    onReuseLastSeed = onReuseLastSeed,
                    onSwapDimensions = onSwapDimensions,
                    onApplyAspectRatio = onApplyAspectRatio,
                    onScaleResolution = onScaleResolution,
                    showReset = false,
                    onResetToPresetDefaults = onResetToPresetDefaults,
                )

                if (showCompositionControls) {
                    CompositionPanelContent(
                        state = state,
                        onGenerateStructuredJson = onGenerateStructuredJson,
                        onRetryStagedJson = onRetryStagedJson,
                        onStartOverComposition = onStartOverComposition,
                        onCompositionMutation = onCompositionMutation,
                        onRunCompositionAction = onRunCompositionAction,
                        onUndoComposition = onUndoComposition,
                        onRedoComposition = onRedoComposition,
                        onCompositionDescriptionChange = onCompositionDescriptionChange,
                        onCompositionTextChange = onCompositionTextChange,
                        onCompositionPaletteChange = onCompositionPaletteChange,
                        onCompositionElementSelected = onCompositionElementSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun LlmDebugConsole(
    entries: List<LlmDebugEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size, entries.lastOrNull()?.response, entries.lastOrNull()?.error) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("LLM debug console", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Session only · ${entries.size} calls",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = "Clear",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = entries.isNotEmpty(), onClick = onClear)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "LLM calls will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        LlmDebugCall(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LlmDebugCall(entry: LlmDebugEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                    "#${entry.id}  ${LLM_DEBUG_TIME_FORMAT.format(entry.startedAt.atZone(ZoneId.systemDefault()))}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    entry.model,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DebugPromptBlock("SYSTEM", entry.systemPrompt.ifBlank { "(none)" })
                DebugPromptBlock("PROMPT", entry.userPrompt.ifBlank { "(none)" })
                when {
                    entry.error != null -> DebugPromptBlock("ERROR", entry.error, MaterialTheme.colorScheme.error)
                    entry.response != null -> DebugPromptBlock("RESPONSE", entry.response)
                    else -> DebugPromptBlock("RESPONSE", "Waiting for LLM...", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DebugPromptBlock(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Spacer(Modifier.height(9.dp))
    Text(
        label,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        value,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = color,
    )
}

private val LLM_DEBUG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
private fun PromptTabContent(
    state: GenerationUiState,
    showNegativePrompt: Boolean,
    onPromptChange: (String) -> Unit,
    onPromptCommit: () -> Unit,
    onUndoPrompt: () -> Unit,
    onRedoPrompt: () -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onEnhancePrompt: () -> Unit,
) {
    TextPromptPanel(
        state = state,
        showNegativePrompt = showNegativePrompt,
        onPromptChange = onPromptChange,
        onPromptCommit = onPromptCommit,
        onUndoPrompt = onUndoPrompt,
        onRedoPrompt = onRedoPrompt,
        onNegativePromptChange = onNegativePromptChange,
        onEnhancePrompt = onEnhancePrompt,
    )
}

@Composable
private fun CompositionPanelContent(
    state: GenerationUiState,
    onGenerateStructuredJson: () -> Unit,
    onRetryStagedJson: () -> Unit,
    onStartOverComposition: () -> Unit,
    onCompositionMutation: (CompositionMutation) -> Unit,
    onRunCompositionAction: (CompositionAction) -> Unit,
    onUndoComposition: () -> Unit,
    onRedoComposition: () -> Unit,
    onCompositionDescriptionChange: (Int, String) -> Unit,
    onCompositionTextChange: (Int, String) -> Unit,
    onCompositionPaletteChange: (Int, List<String>) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
) {
    var highlightStyleFields by remember { mutableStateOf(false) }
    var highlightCompositionFields by remember { mutableStateOf(false) }
    var highlightHighLevelDescription by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.ideogram.isGeneratingJson) {
        if (state.ideogram.isGeneratingJson) focusManager.clearFocus(force = true)
    }

    Label("Composition")
    CompositionGenerationHeader(
        state = state,
        onGenerateComposition = onGenerateStructuredJson,
        onRetry = onRetryStagedJson,
    )
    if (state.ideogram.document != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (state.ideogram.isGeneratingJson) 0.62f else 1f },
                verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
            ) {
                IdeogramCompositionForm(
                    state = state,
                    onMutation = onCompositionMutation,
                    onRunAction = onRunCompositionAction,
                    onStartOver = onStartOverComposition,
                    onUndo = onUndoComposition,
                    onRedo = onRedoComposition,
                    onCompositionDescriptionChange = onCompositionDescriptionChange,
                    onCompositionTextChange = onCompositionTextChange,
                    onCompositionPaletteChange = onCompositionPaletteChange,
                    onCompositionElementSelected = onCompositionElementSelected,
                    highlightStyleFields = highlightStyleFields,
                    highlightCompositionFields = highlightCompositionFields,
                    highlightHighLevelDescription = highlightHighLevelDescription,
                    onHighlightStyleFieldsChange = { highlightStyleFields = it },
                    onHighlightCompositionFieldsChange = { highlightCompositionFields = it },
                    onHighlightHighLevelDescriptionChange = { highlightHighLevelDescription = it },
                )
            }
            if (state.ideogram.isGeneratingJson) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.all { it.scrollDelta == Offset.Zero }) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
                )
            }
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
    inputsEnabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Prompt")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DeskSubtleTextButton(
                    icon = Icons.Default.AutoFixHigh,
                    text = if (state.isEnhancingPrompt) "Enhancing..." else "Enhance",
                    onClick = onEnhancePrompt,
                    enabled = inputsEnabled && state.prompt.isNotBlank() && !state.isEnhancingPrompt,
                )
                DeskMiniIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Previous prompt",
                    onClick = onUndoPrompt,
                    enabled = inputsEnabled && state.canUndoPrompt,
                )
                DeskMiniIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Next prompt",
                    onClick = onRedoPrompt,
                    enabled = inputsEnabled && state.canRedoPrompt,
                )
            }
        }
        PaddedTextArea(
            value = state.prompt,
            onValueChange = onPromptChange,
            onFocusLost = onPromptCommit,
            enabled = inputsEnabled && !state.isEnhancingPrompt,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 142.dp),
        )

        if (showNegativePrompt) {
            Label("Negative Prompt")
            PaddedTextArea(
                value = state.negativePrompt,
                onValueChange = onNegativePromptChange,
                enabled = inputsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 74.dp),
            )
        }
    }
}

@Composable
private fun LoraPanel(
    state: GenerationUiState,
    onSearchChange: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onReload: () -> Unit,
    onToggleLora: (String) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    modelDir: String,
) {
    val query = state.loraSearchQuery.trim()
    val loraFolder = File(modelDir.trim().ifBlank { "models" }, "lora").absolutePath
    val filtered = state.loraModels.filter { lora ->
        query.isBlank() ||
            lora.cleanName.contains(query, ignoreCase = true) ||
            lora.triggerWord.contains(query, ignoreCase = true)
    }
    val visible = filtered.take(8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
            .padding(DeskLayoutGap),
        verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.loraPanelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Label("LoRAs")
                if (state.activeLoraCount > 0) {
                    DeskStatusBadge("${state.activeLoraCount} active", DeskStatusTone.Info)
                }
            }
            DeskSubtleTextButton(
                icon = Icons.Default.Refresh,
                text = if (state.isLoadingLoras) "Loading..." else "Refresh",
                onClick = onReload,
                enabled = !state.isLoadingLoras,
            )
        }

        if (state.loraPanelExpanded) {
            DeskTextField(
                label = "",
                value = state.loraSearchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search LoRAs...",
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                state.isLoadingLoras -> Text(
                    text = "Loading LoRAs...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.loraModels.isEmpty() -> Text(
                    text = "No LoRAs found in $loraFolder. Place .safetensors, .gguf, .ckpt, or .pth files there, then refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                filtered.isEmpty() -> Text(
                    text = "No LoRAs match the current search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    visible.forEach { lora ->
                        val active = state.activeLoras.firstOrNull { it.id == lora.id }
                        LoraRow(
                            name = lora.cleanName,
                            triggerWord = lora.triggerWord,
                            checked = active != null,
                            weight = active?.weight ?: 1.0,
                            onToggle = { onToggleLora(lora.id) },
                            onWeightChange = { onWeightChange(lora.id, it) },
                        )
                    }
                    if (filtered.size > visible.size) {
                        Text(
                            text = "${filtered.size - visible.size} more LoRAs hidden by the compact list.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoraRow(
    name: String,
    triggerWord: String,
    checked: Boolean,
    weight: Double,
    onToggle: () -> Unit,
    onWeightChange: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(DeskLayoutGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (triggerWord.isNotBlank()) {
                    Text(
                        text = "Trigger: $triggerWord",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (checked) {
                Text(
                    text = "%.2f".format(Locale.US, weight),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (checked) {
            Slider(
                value = weight.toFloat(),
                onValueChange = { onWeightChange(it.toDouble()) },
                valueRange = 0f..2f,
                steps = 0,
            )
        }
    }
}

@Composable
private fun CompositionGenerationHeader(
    state: GenerationUiState,
    onGenerateComposition: () -> Unit,
    onRetry: () -> Unit,
) {
    val hasComposition = state.ideogram.document != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hasComposition && !state.ideogram.isGeneratingJson) {
            DeskButton(
                onClick = onGenerateComposition,
                enabled = state.prompt.isNotBlank() && !state.ideogram.isGeneratingJson,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            ) {
                ButtonContent(
                    icon = Icons.Default.AutoFixHigh,
                    text = "Generate composition",
                )
            }
        }
        if (state.ideogram.jsonError != null || state.ideogram.failedStagedStep != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.ideogram.jsonError ?: when {
                        state.ideogram.stagedElementIndex != null ->
                            "Element details ${state.ideogram.stagedElementIndex}/${state.ideogram.stagedElementCount}"
                        else -> state.ideogram.jsonStatus
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.ideogram.jsonError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (state.ideogram.failedStagedStep != null) {
                    DeskSubtleTextButton(
                        icon = Icons.Default.Refresh,
                        text = "Retry step",
                        onClick = onRetry,
                        enabled = !state.ideogram.isGeneratingJson,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzeImagePanel(
    state: GenerationUiState,
    onModeChange: (CaptureImageMode) -> Unit,
    onUploadSelected: (File) -> Unit,
    onStartCapture: () -> Unit,
    onApplyCapture: () -> Unit,
) {
    val capture = state.ideogramCapture
    val busy = capture.isBusy
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
            .padding(DeskLayoutGap),
        verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Image analysis")
            if (busy) DeskStatusBadge(capture.status.ifBlank { "Working..." }, DeskStatusTone.Info)
        }
        if (busy || capture.progressLabel.isNotBlank() || capture.progressDetail.isNotBlank()) {
            CaptureProgressBlock(state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = capture.activeSourceLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DeskSubtleTextButton(
                icon = Icons.Default.Add,
                text = "Choose",
                onClick = { chooseCaptureImageFile()?.let(onUploadSelected) },
                enabled = !busy,
            )
        }
        DeskCompactDropdownField(
            label = "Apply mode",
            value = capture.mode.name,
            options = CaptureImageMode.entries.map { it.name },
            onValueChange = { value ->
                CaptureImageMode.entries.firstOrNull { it.name == value }?.let(onModeChange)
            },
            modifier = Modifier.fillMaxWidth(),
            minMenuWidth = 120.dp,
        )
        DeskButton(
            onClick = onStartCapture,
            enabled = !busy && capture.hasSource,
            modifier = Modifier.fillMaxWidth().height(38.dp),
        ) {
            ButtonContent(Icons.Default.ImageSearch, if (capture.isInspecting) "Inspecting..." else "Analyze image")
        }
        capture.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.ideogram.document != null) {
            DeskButton(
                onClick = onApplyCapture,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(38.dp),
            ) {
                ButtonContent(Icons.Default.PlayArrow, "Apply to Generate")
            }
        }
    }
}

@Composable
private fun CaptureProgressBlock(state: GenerationUiState) {
    val capture = state.ideogramCapture
    val progress = capture.progress.coerceIn(0f, 1f)
    val percent = (progress * 100f).roundToInt().coerceIn(0, 100)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = capture.progressLabel.ifBlank {
                    when {
                        capture.isInspecting -> "Global composition pass"
                        capture.isRefining -> "Element detail pass"
                        else -> "Analysis ready"
                    }
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        DeskProgressTrack(
            progress = progress.coerceAtLeast(if (capture.isBusy) 0.06f else 0f),
            modifier = Modifier.fillMaxWidth().height(5.dp),
        )
        Text(
            text = capture.progressDetail.ifBlank { capture.status.ifBlank { "Waiting for the next VLLM step." } },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IdeogramCompositionForm(
    state: GenerationUiState,
    onMutation: (CompositionMutation) -> Unit,
    onRunAction: (CompositionAction) -> Unit,
    onStartOver: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompositionDescriptionChange: (Int, String) -> Unit,
    onCompositionTextChange: (Int, String) -> Unit,
    onCompositionPaletteChange: (Int, List<String>) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
    highlightStyleFields: Boolean,
    highlightCompositionFields: Boolean,
    highlightHighLevelDescription: Boolean,
    onHighlightStyleFieldsChange: (Boolean) -> Unit,
    onHighlightCompositionFieldsChange: (Boolean) -> Unit,
    onHighlightHighLevelDescriptionChange: (Boolean) -> Unit,
    showDocumentActions: Boolean = true,
    showAiActions: Boolean = true,
    showAdditionalFields: Boolean = true,
    showGenerateElementActions: Boolean = true,
    showElementAiActions: Boolean = true,
) {
    CompositionDocumentEditor(
        state = state,
        onMutation = onMutation,
        onRunAction = onRunAction,
        onStartOver = onStartOver,
        onUndo = onUndo,
        onRedo = onRedo,
        highlightStyleFields = highlightStyleFields,
        highlightCompositionFields = highlightCompositionFields,
        highlightHighLevelDescription = highlightHighLevelDescription,
        onHighlightStyleFieldsChange = onHighlightStyleFieldsChange,
        onHighlightCompositionFieldsChange = onHighlightCompositionFieldsChange,
        onHighlightHighLevelDescriptionChange = onHighlightHighLevelDescriptionChange,
        showDocumentActions = showDocumentActions,
        showAiActions = showAiActions,
        showAdditionalFields = showAdditionalFields,
        showGenerateElementActions = showGenerateElementActions,
    )
    IdeogramElementEditor(
        elements = ideogramElementPreviews(state.ideogram.jsonPrompt),
        elementIds = state.compositionElementIds,
        selectedIndex = state.selectedCompositionElementIndex,
        activeImproveAction = state.activeCompositionImproveAction,
        onElementSelected = onCompositionElementSelected,
        onMutation = onMutation,
        onRunAction = onRunAction,
        onElementDescriptionChange = onCompositionDescriptionChange,
        onElementTextChange = onCompositionTextChange,
        onElementPaletteChange = onCompositionPaletteChange,
        highlightPlacement = highlightCompositionFields,
        onHighlightHighLevelDescriptionChange = onHighlightHighLevelDescriptionChange,
        showAiActions = showElementAiActions,
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CompositionDocumentEditor(
    state: GenerationUiState,
    onMutation: (CompositionMutation) -> Unit,
    onRunAction: (CompositionAction) -> Unit,
    onStartOver: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    highlightStyleFields: Boolean,
    highlightCompositionFields: Boolean,
    highlightHighLevelDescription: Boolean,
    onHighlightStyleFieldsChange: (Boolean) -> Unit,
    onHighlightCompositionFieldsChange: (Boolean) -> Unit,
    onHighlightHighLevelDescriptionChange: (Boolean) -> Unit,
    showDocumentActions: Boolean,
    showAiActions: Boolean,
    showAdditionalFields: Boolean,
    showGenerateElementActions: Boolean,
) {
    val document = state.ideogram.document ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Overview")
            if (showDocumentActions) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DeskSubtleTextButton(
                    icon = Icons.Default.RestartAlt,
                    text = "Start over",
                    onClick = onStartOver,
                    enabled = !state.ideogram.isGeneratingJson,
                )
                DeskMiniIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo composition change",
                    onClick = onUndo,
                    enabled = state.ideogram.canUndo,
                )
                DeskMiniIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo composition change",
                    onClick = onRedo,
                    enabled = state.ideogram.canRedo,
                )
            }
        }
        CompositionEditField(
            label = "High-level description",
            value = document.highLevelDescription,
            onCommit = { onMutation(CompositionMutation.UpdateHighLevelDescription(it)) },
            onImprove = if (showAiActions) {
                { onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.HighLevelDescription)) }
            } else null,
            isImproving = state.activeCompositionImproveAction == CompositionAction.ImproveField(CompositionImproveTarget.HighLevelDescription).actionId,
            improveEnabled = state.activeCompositionImproveAction == null,
            minHeight = 82.dp,
            highlighted = highlightHighLevelDescription,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Style")
            if (showAiActions) DeskSubtleTextButton(
                icon = Icons.Default.AutoFixHigh,
                text = if (state.activeCompositionImproveAction == CompositionAction.ImproveStyle.actionId) "Improving..." else "Improve style",
                onClick = { onRunAction(CompositionAction.ImproveStyle) },
                enabled = state.activeCompositionImproveAction == null,
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { onHighlightStyleFieldsChange(true) }
                    .onPointerEvent(PointerEventType.Exit) { onHighlightStyleFieldsChange(false) },
            )
        }
        CompositionEditField("Aesthetics", document.style.aesthetics, {
            onMutation(CompositionMutation.UpdateStyleField(IdeogramStyleField.Aesthetics, it))
        }, onImprove = if (showAiActions) {
            { onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.StyleField(IdeogramStyleField.Aesthetics))) }
        } else null,
            isImproving = state.activeCompositionImproveAction == CompositionAction.ImproveField(CompositionImproveTarget.StyleField(IdeogramStyleField.Aesthetics)).actionId,
            improveEnabled = state.activeCompositionImproveAction == null,
            highlighted = highlightStyleFields)
        CompositionEditField("Lighting", document.style.lighting, {
            onMutation(CompositionMutation.UpdateStyleField(IdeogramStyleField.Lighting, it))
        }, onImprove = if (showAiActions) {
            { onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.StyleField(IdeogramStyleField.Lighting))) }
        } else null,
            isImproving = state.activeCompositionImproveAction == CompositionAction.ImproveField(CompositionImproveTarget.StyleField(IdeogramStyleField.Lighting)).actionId,
            improveEnabled = state.activeCompositionImproveAction == null,
            highlighted = highlightStyleFields)
        CompositionEditField("Medium", document.style.medium, {
            onMutation(CompositionMutation.UpdateStyleField(IdeogramStyleField.Medium, it))
        }, onImprove = if (showAiActions) {
            { onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.StyleField(IdeogramStyleField.Medium))) }
        } else null,
            isImproving = state.activeCompositionImproveAction == CompositionAction.ImproveField(CompositionImproveTarget.StyleField(IdeogramStyleField.Medium)).actionId,
            improveEnabled = state.activeCompositionImproveAction == null,
            highlighted = highlightStyleFields)
        val usesPhoto = document.style.photo != null
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (usesPhoto) "Photo" else "Art style",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeskSubtleTextButton(
                icon = Icons.Default.SwapHoriz,
                text = if (usesPhoto) "Use art style" else "Use photo",
                onClick = {
                    onMutation(
                        CompositionMutation.UpdateStyleField(
                            if (usesPhoto) IdeogramStyleField.ArtStyle else IdeogramStyleField.Photo,
                            if (usesPhoto) "illustration" else "photograph",
                        ),
                    )
                },
            )
        }
        CompositionEditField(
            label = if (usesPhoto) "Photo description" else "Art-style description",
            value = document.style.photo ?: document.style.artStyle.orEmpty(),
            onCommit = {
                onMutation(
                    CompositionMutation.UpdateStyleField(
                        if (usesPhoto) IdeogramStyleField.Photo else IdeogramStyleField.ArtStyle,
                        it,
                    ),
                )
            },
            onImprove = {
                onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.StyleField(if (usesPhoto) IdeogramStyleField.Photo else IdeogramStyleField.ArtStyle)))
            }.takeIf { showAiActions },
            isImproving = state.activeCompositionImproveAction == CompositionAction.ImproveField(
                CompositionImproveTarget.StyleField(if (usesPhoto) IdeogramStyleField.Photo else IdeogramStyleField.ArtStyle),
            ).actionId,
            improveEnabled = state.activeCompositionImproveAction == null,
            highlighted = highlightStyleFields,
        )
        PaletteEditor(
            label = "Global palette",
            colors = document.style.colorPalette,
            maxColors = 16,
            onColorsChange = { onMutation(CompositionMutation.UpdateGlobalPalette(it)) },
            onSuggest = if (showAiActions) { { onRunAction(CompositionAction.SuggestPalette(PaletteTarget.Global)) } } else null,
            isSuggesting = state.activeCompositionImproveAction == CompositionAction.SuggestPalette(PaletteTarget.Global).actionId,
            suggestEnabled = state.activeCompositionImproveAction == null,
            highlighted = highlightStyleFields,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Composition")
            if (showAiActions) DeskSubtleTextButton(
                icon = Icons.Default.AutoFixHigh,
                text = if (state.activeCompositionImproveAction == CompositionAction.ImproveComposition.actionId) "Improving..." else "Improve composition",
                onClick = { onRunAction(CompositionAction.ImproveComposition) },
                enabled = state.activeCompositionImproveAction == null,
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { onHighlightCompositionFieldsChange(true) }
                    .onPointerEvent(PointerEventType.Exit) { onHighlightCompositionFieldsChange(false) },
            )
        }
        CompositionEditField(
            label = "Background",
            value = document.background,
            onCommit = { onMutation(CompositionMutation.UpdateBackground(it)) },
            onImprove = if (showAiActions) {
                { onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.Background)) }
            } else null,
            isImproving = state.activeCompositionImproveAction == CompositionAction.ImproveField(CompositionImproveTarget.Background).actionId,
            improveEnabled = state.activeCompositionImproveAction == null,
            minHeight = 70.dp,
            highlighted = highlightCompositionFields,
        )
        if (showAdditionalFields && document.additionalFields.isNotEmpty()) {
            Label("Additional fields")
            document.additionalFields.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.weight(1f)) {
                        CompositionEditField(
                            label = field.path,
                            value = field.jsonValue,
                            onCommit = { onMutation(CompositionMutation.UpdateAdditionalField(field.path, it)) },
                            minHeight = 42.dp,
                            singleLine = true,
                        )
                    }
                    DeskIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Remove ${field.path}",
                        onClick = { onMutation(CompositionMutation.RemoveAdditionalField(field.path)) },
                        tooltip = "Remove field",
                    )
                }
            }
        }
        if (showGenerateElementActions) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Elements")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DeskSubtleTextButton(
                    icon = Icons.Default.Add,
                    text = "Add object",
                    onClick = { onMutation(CompositionMutation.AddElement("obj")) },
                    enabled = state.activeCompositionImproveAction == null,
                )
                DeskSubtleTextButton(
                    icon = Icons.Default.Add,
                    text = "Add text",
                    onClick = { onMutation(CompositionMutation.AddElement("text")) },
                    enabled = state.activeCompositionImproveAction == null,
                )
            }
        }
        }
    }
}

@Composable
private fun CompactCompositionInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val shape = RoundedCornerShape(5.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CompositionEditField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    onImprove: (() -> Unit)? = null,
    isImproving: Boolean = false,
    improveEnabled: Boolean = true,
    minHeight: Dp = 42.dp,
    singleLine: Boolean = false,
    highlighted: Boolean = false,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onImprove != null) {
                DeskSubtleTextButton(
                    icon = Icons.Default.AutoFixHigh,
                    text = if (isImproving) "Improving..." else "Improve",
                    onClick = onImprove,
                    enabled = improveEnabled && value.isNotBlank(),
                )
            }
        }
        val shape = RoundedCornerShape(5.dp)
        var hadFocus by remember { mutableStateOf(false) }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .onFocusChanged { focusState ->
                    if (hadFocus && !focusState.isFocused && draft != value) onCommit(draft)
                    hadFocus = focusState.isFocused
                }
                .clip(shape)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.surface,
                )
                .border(
                    1.dp,
                    if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                )
                .padding(horizontal = 9.dp, vertical = 7.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun IdeogramElementEditor(
    elements: List<IdeogramCompositionElement>,
    elementIds: List<String>,
    selectedIndex: Int,
    activeImproveAction: String?,
    onElementSelected: (Int) -> Unit,
    onMutation: (CompositionMutation) -> Unit,
    onRunAction: (CompositionAction) -> Unit,
    onElementDescriptionChange: (Int, String) -> Unit,
    onElementTextChange: (Int, String) -> Unit,
    onElementPaletteChange: (Int, List<String>) -> Unit,
    highlightPlacement: Boolean,
    onHighlightHighLevelDescriptionChange: (Boolean) -> Unit,
    showAiActions: Boolean,
) {
    LaunchedEffect(elements.size, selectedIndex) {
        if (elements.isNotEmpty() && selectedIndex !in elements.indices) {
            onElementSelected(elements.lastIndex)
        }
    }
    if (elements.isEmpty()) {
        Text("No valid elements to edit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    elements.forEachIndexed { index, element ->
        ElementPreviewRow(
            index = index + 1,
            type = element.type,
            textValue = element.text.orEmpty(),
            desc = element.description,
            bbox = element.bbox,
            elementColor = compositionElementColor(index, elementIds.getOrNull(index)),
            colors = element.colorPalette,
            selected = index == selectedIndex,
            onClick = { onElementSelected(index) },
            onTypeChange = { onMutation(CompositionMutation.UpdateElementType(index, it)) },
            onDelete = { onMutation(CompositionMutation.RemoveElement(index)) },
            isDeleting = false,
            onBboxChange = { onMutation(CompositionMutation.UpdateElementBbox(index, it)) },
            onDescriptionChange = { onElementDescriptionChange(index, it) },
            onImproveDescription = { onRunAction(CompositionAction.ImproveField(CompositionImproveTarget.ElementDescription(index))) },
            isImprovingDescription = activeImproveAction == CompositionAction.ImproveField(CompositionImproveTarget.ElementDescription(index)).actionId,
            improveDescriptionEnabled = activeImproveAction == null,
            onTextChange = { onElementTextChange(index, it) },
            onPaletteChange = { onElementPaletteChange(index, it) },
            onSuggestPalette = { onRunAction(CompositionAction.SuggestPalette(PaletteTarget.Element(index))) },
            isSuggestingPalette = activeImproveAction == CompositionAction.SuggestPalette(PaletteTarget.Element(index)).actionId,
            suggestPaletteEnabled = activeImproveAction == null,
            onRegenerate = { onRunAction(CompositionAction.RegenerateElement(index)) },
            isRegenerating = activeImproveAction == CompositionAction.RegenerateElement(index).actionId,
            actionEnabled = activeImproveAction == null,
            highlightPlacement = highlightPlacement,
            onHighlightHighLevelDescriptionChange = onHighlightHighLevelDescriptionChange,
            showAiActions = showAiActions,
        )
    }
}

@Composable
private fun IdeogramCompositionCanvas(
    elements: List<IdeogramCompositionElement>,
    elementIds: List<String>,
    width: Int,
    height: Int,
    selectedIndex: Int,
    image: GeneratedImage?,
    showOverlay: Boolean,
    showGrid: Boolean,
    outputDir: String,
    onElementSelected: (Int) -> Unit,
    onBboxEditStart: () -> Unit,
    onElementBboxChange: (Int, List<Int>) -> Unit,
    onBboxEditEnd: () -> Unit,
    onBboxEditCancel: () -> Unit,
    modifier: Modifier = Modifier,
    editingEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val latestElements by rememberUpdatedState(elements)
    val latestOnElementBboxChange by rememberUpdatedState(onElementBboxChange)
    val latestOnElementSelected by rememberUpdatedState(onElementSelected)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val contentWidth = image?.bitmap?.width ?: width
        val contentHeight = image?.bitmap?.height ?: height
        val fittedCanvas = fitCanvasRect(
            containerWidth = with(density) { maxWidth.toPx() },
            containerHeight = with(density) { maxHeight.toPx() },
            contentWidth = contentWidth,
            contentHeight = contentHeight,
        )
        val canvasWidthPx = fittedCanvas.width.coerceAtLeast(1f)
        val canvasHeightPx = fittedCanvas.height.coerceAtLeast(1f)
        val canvasWidth = with(density) { canvasWidthPx.toDp() }
        val canvasHeight = with(density) { canvasHeightPx.toDp() }
        val canvasElements = elements.mapIndexedNotNull { index, element ->
            val rectPx = ideogramBboxToCanvasRect(element.bbox, canvasWidthPx, canvasHeightPx) ?: return@mapIndexedNotNull null
            val rectDp = ideogramBboxToCanvasRect(element.bbox, canvasWidth.value, canvasHeight.value) ?: return@mapIndexedNotNull null
            CompositionCanvasElement(index, element, rectPx, rectDp, compositionElementColor(index, elementIds.getOrNull(index)))
        }
        val handleHitSizePx = with(density) { 32.dp.toPx() }
        val repeatClickDistancePx = with(density) { 8.dp.toPx() }
        var lastSelectionClick by remember { mutableStateOf<CompositionSelectionClick?>(null) }
        fun selectedResizeHandleContains(offset: Offset): Boolean {
            val selected = canvasElements.firstOrNull { it.index == selectedIndex } ?: return false
            if (!selected.rectPx.contains(offset)) return false
            val localOffset = Offset(
                x = offset.x - selected.rectPx.left,
                y = offset.y - selected.rectPx.top,
            )
            return isResizeHandleHit(localOffset, selected.rectPx.width, selected.rectPx.height, handleHitSizePx)
        }
        fun selectElementAt(offset: Offset): Int? {
            if (selectedResizeHandleContains(offset)) {
                lastSelectionClick = null
                return null
            }
            val hits = canvasElements
                .filter { it.rectPx.contains(offset) }
                .sortedWith(
                    compareBy<CompositionCanvasElement> { it.rectPx.width * it.rectPx.height }
                        .thenBy { it.index },
                )
            if (hits.isEmpty()) {
                lastSelectionClick = null
                return null
            }

            val hitIndexes = hits.map { it.index }
            val lastClick = lastSelectionClick
            val isRepeatClick = lastClick != null &&
                lastClick.hitIndexes == hitIndexes &&
                lastClick.offset.distanceSquaredTo(offset) <= repeatClickDistancePx * repeatClickDistancePx

            val nextIndex = if (isRepeatClick) {
                val currentHitPosition = hitIndexes.indexOf(selectedIndex)
                if (currentHitPosition >= 0) hitIndexes[(currentHitPosition + 1) % hitIndexes.size] else hitIndexes.first()
            } else {
                hitIndexes.first()
            }
            lastSelectionClick = CompositionSelectionClick(offset, hitIndexes)
            return nextIndex
        }

        ImageContextMenuArea(
            images = image?.let {
                listOf(it.toImageContextMenuData(outputDir, fallbackFileName = "generated-image-1.png"))
            } ?: emptyList(),
        ) {
            Box(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(canvasHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(canvasElements, selectedIndex, image == null || showOverlay, editingEnabled) {
                        if (editingEnabled && (image == null || showOverlay)) {
                            awaitPointerEventScope {
                                var trackingClick = false
                                var start = Offset.Zero
                                var isDrag = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (!trackingClick && change.pressed && !change.previousPressed) {
                                        trackingClick = true
                                        start = change.position
                                        isDrag = false
                                    } else if (trackingClick) {
                                        if (change.position.distanceSquaredTo(start) > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                            isDrag = true
                                        }
                                        if (!change.pressed) {
                                            if (!isDrag) selectElementAt(start)?.let(latestOnElementSelected)
                                            trackingClick = false
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                if (image != null) {
                    Image(
                        bitmap = image.bitmap,
                        contentDescription = "Generated image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                if (image == null || showOverlay) {
                    if (showGrid) {
                        IdeogramCompositionGrid()
                    }
                    canvasElements.sortedBy { if (it.index == selectedIndex) 1 else 0 }.forEach { canvasElement ->
                        val index = canvasElement.index
                        val element = canvasElement.element
                        val elementRect = canvasElement.rectDp
                        val top = elementRect.top.dp
                        val left = elementRect.left.dp
                        val bottom = elementRect.bottom.dp
                        val right = elementRect.right.dp
                        val boxColor = canvasElement.color
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .offset(left, top)
                                .width((right - left).coerceAtLeast(8.dp))
                                .height((bottom - top).coerceAtLeast(8.dp))
                                .zIndex(if (isSelected) 2f else 1f)
                                .border(if (isSelected) 4.dp else 2.dp, boxColor, RoundedCornerShape(2.dp))
                                .background(boxColor.copy(alpha = if (isSelected) 0.16f else 0.08f))
                                .then(
                                    if (isSelected && editingEnabled) {
                                        Modifier.pointerInput(index, canvasWidthPx, canvasHeightPx) {
                                            var startBbox = emptyList<Int>()
                                            var dragX = 0f
                                            var dragY = 0f
                                            detectDragGestures(
                                                onDragStart = {
                                                    onBboxEditStart()
                                                    latestOnElementSelected(index)
                                                    startBbox = latestElements.getOrNull(index)?.bbox.orEmpty()
                                                    dragX = 0f
                                                    dragY = 0f
                                                },
                                                onDrag = { _, dragAmount ->
                                                    dragX += dragAmount.x
                                                    dragY += dragAmount.y
                                                    val deltaX = canvasDeltaToIdeogram(dragX, canvasWidthPx)
                                                    val deltaY = canvasDeltaToIdeogram(dragY, canvasHeightPx)
                                                    moveCompositionBbox(startBbox, deltaX, deltaY)?.let { nextBbox ->
                                                        latestOnElementBboxChange(index, nextBbox)
                                                    }
                                                },
                                                onDragEnd = onBboxEditEnd,
                                                onDragCancel = onBboxEditCancel,
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(4.dp),
                        ) {
                            Text(
                                text = element.text?.takeIf { element.type == "text" && it.isNotBlank() } ?: "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = boxColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSelected && editingEnabled) {
                                CompositionResizeHandle.values().forEach { handle ->
                                    CompositionResizeHandleBox(
                                        ownerKey = index,
                                        handle = handle,
                                        color = boxColor,
                                        startBbox = { latestElements.getOrNull(index)?.bbox.orEmpty() },
                                        onEditStart = onBboxEditStart,
                                        onDragStart = { latestOnElementSelected(index) },
                                        onDrag = { startBbox, deltaX, deltaY ->
                                            val deltaNormX = canvasDeltaToIdeogram(deltaX, canvasWidthPx)
                                            val deltaNormY = canvasDeltaToIdeogram(deltaY, canvasHeightPx)
                                            resizeCompositionBbox(startBbox, deltaNormX, deltaNormY, handle)?.let { nextBbox ->
                                                latestOnElementBboxChange(index, nextBbox)
                                            }
                                        },
                                        onDragEnd = onBboxEditEnd,
                                        onDragCancel = onBboxEditCancel,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdeogramCompositionGrid() {
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val centerLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    Canvas(modifier = Modifier.fillMaxSize().zIndex(0.5f)) {
        val lineWidth = 1.dp.toPx()
        val centerLineWidth = 1.5.dp.toPx()
        for (index in 1 until 10) {
            val fraction = index / 10f
            val isCenter = index == 5
            val color = if (isCenter) centerLineColor else lineColor
            val strokeWidth = if (isCenter) centerLineWidth else lineWidth
            drawLine(
                color = color,
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = color,
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
                strokeWidth = strokeWidth,
            )
        }
    }
}

private data class CompositionCanvasElement(
    val index: Int,
    val element: IdeogramCompositionElement,
    val rectPx: CanvasRect,
    val rectDp: CanvasRect,
    val color: Color,
)

private data class CompositionSelectionClick(
    val offset: Offset,
    val hitIndexes: List<Int>,
)

private fun CanvasRect.contains(offset: Offset): Boolean =
    offset.x >= left && offset.x <= right && offset.y >= top && offset.y <= bottom

private fun Offset.distanceSquaredTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
}

private fun isResizeHandleHit(offset: Offset, width: Float, height: Float, handleSize: Float): Boolean {
    val nearLeft = offset.x <= handleSize
    val nearRight = offset.x >= width - handleSize
    val nearTop = offset.y <= handleSize
    val nearBottom = offset.y >= height - handleSize
    return (nearLeft || nearRight) && (nearTop || nearBottom)
}

private fun compositionElementColor(index: Int, elementId: String?): Color {
    val colors = listOf(
        Color(0xFF2D7FF9),
        Color(0xFFE05D5D),
        Color(0xFF2EA36B),
        Color(0xFFB36BDB),
        Color(0xFFD89025),
        Color(0xFF0097A7),
        Color(0xFFC04D8B),
        Color(0xFF6A7FDB),
        Color(0xFF8B8F22),
        Color(0xFFDD6F2A),
    )
    val colorKey = elementId?.hashCode() ?: index
    return colors[colorKey.floorMod(colors.size)]
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

@Composable
private fun BoxScope.CompositionResizeHandleBox(
    ownerKey: Int,
    handle: CompositionResizeHandle,
    color: Color,
    startBbox: () -> List<Int>,
    onEditStart: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (List<Int>, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
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
            .pointerInput(ownerKey, handle) {
                var capturedBbox = emptyList<Int>()
                var dragX = 0f
                var dragY = 0f
                detectDragGestures(
                    onDragStart = {
                        onEditStart()
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
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun ElementPreviewRow(
    index: Int,
    type: String,
    textValue: String,
    desc: String,
    bbox: List<Int>,
    elementColor: Color,
    colors: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    onTypeChange: (String) -> Unit,
    onDelete: () -> Unit,
    isDeleting: Boolean,
    onBboxChange: (List<Int>?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onImproveDescription: () -> Unit,
    isImprovingDescription: Boolean,
    improveDescriptionEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onPaletteChange: (List<String>) -> Unit,
    onSuggestPalette: () -> Unit,
    isSuggestingPalette: Boolean,
    suggestPaletteEnabled: Boolean,
    onRegenerate: () -> Unit,
    isRegenerating: Boolean,
    actionEnabled: Boolean,
    highlightPlacement: Boolean,
    onHighlightHighLevelDescriptionChange: (Boolean) -> Unit,
    showAiActions: Boolean,
) {
    val shape = RoundedCornerShape(DeskControlCornerRadius)
    var highlightRegeneratedFields by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (selected) expanded = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) elementColor else elementColor.copy(alpha = 0.55f),
                shape,
            )
            .padding(horizontal = DeskControlSpacing, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .clickable {
                    onClick()
                    expanded = !expanded
                }
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse element $index" else "Expand element $index",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(elementColor.copy(alpha = if (selected) 0.95f else 0.75f))
                    .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
            )
            Text(
                text = "$index / $type",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) elementColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (type == "text" && textValue.isNotBlank()) "“$textValue”" else desc,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = compositionBboxLabel(bbox),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (highlightPlacement) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else Color.Transparent,
                    )
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                colors.take(4).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(ideogramComposeColor(color) ?: MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), CircleShape),
                    )
                }
            }
        }
        if (expanded) {
            if (type == "text") {
                InlineCompositionField(
                    value = textValue,
                    onValueChange = onTextChange,
                    placeholder = "Text",
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                    singleLine = true,
                )
            }
            InlineCompositionField(
                value = desc,
                onValueChange = onDescriptionChange,
                placeholder = "Description",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                highlighted = highlightRegeneratedFields,
            )
            PaletteEditor(
                label = "Palette",
                colors = colors,
                maxColors = 5,
                onColorsChange = onPaletteChange,
                onSuggest = null,
                highlighted = highlightRegeneratedFields,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showAiActions) {
                    DeskSubtleTextButton(
                        icon = Icons.Default.AutoFixHigh,
                        text = if (isImprovingDescription) "Improving..." else "Improve",
                        onClick = onImproveDescription,
                        enabled = improveDescriptionEnabled && desc.isNotBlank(),
                    )
                    DeskSubtleTextButton(
                        icon = Icons.Default.Refresh,
                        text = if (isRegenerating) "Creating..." else if (desc.isBlank()) "Generate" else "Variant",
                        onClick = onRegenerate,
                        enabled = actionEnabled,
                        modifier = Modifier
                            .onPointerEvent(PointerEventType.Enter) {
                                highlightRegeneratedFields = true
                            }
                            .onPointerEvent(PointerEventType.Exit) {
                                highlightRegeneratedFields = false
                            },
                    )
                    DeskSubtleTextButton(
                        icon = Icons.Default.Palette,
                        text = if (isSuggestingPalette) "Choosing..." else "Colors",
                        onClick = onSuggestPalette,
                        enabled = suggestPaletteEnabled,
                    )
                }
                DeskSubtleTextButton(
                    icon = Icons.Default.SwapHoriz,
                    text = if (type == "text") "To object" else "To text",
                    onClick = { onTypeChange(if (type == "text") "obj" else "text") },
                )
                DeskSubtleTextButton(
                    icon = Icons.Default.CropFree,
                    text = if (bbox.size == 4) "No bounds" else "Add bounds",
                    onClick = { onBboxChange(if (bbox.size == 4) null else listOf(250, 250, 750, 750)) },
                )
                DeskSubtleTextButton(
                    icon = Icons.Default.DeleteOutline,
                    text = if (isDeleting) "Deleting..." else "Delete",
                    onClick = onDelete,
                    enabled = actionEnabled,
                )
            }
        }
    }
}

@Composable
private fun InlineCompositionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    textStyle: androidx.compose.ui.text.TextStyle,
    singleLine: Boolean = false,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(5.dp)
    var draft by remember(value) { mutableStateOf(value) }
    var hadFocus by remember { mutableStateOf(false) }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = modifier
            .onFocusChanged { focusState ->
                if (hadFocus && !focusState.isFocused && draft != value) onValueChange(draft)
                hadFocus = focusState.isFocused
            }
            .clip(shape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (draft.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteEditor(
    label: String,
    colors: List<String>,
    maxColors: Int,
    onColorsChange: (List<String>) -> Unit,
    onSuggest: (() -> Unit)? = null,
    isSuggesting: Boolean = false,
    suggestEnabled: Boolean = true,
    highlighted: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent,
            )
            .border(
                1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onSuggest != null) {
                DeskSubtleTextButton(
                    icon = Icons.Default.AutoFixHigh,
                    text = if (isSuggesting) "Suggesting..." else "Suggest",
                    onClick = onSuggest,
                    enabled = suggestEnabled,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            colors.forEachIndexed { index, color ->
                EditablePaletteSwatch(
                    color = color,
                    onColorChange = { nextColor ->
                        onColorsChange(colors.toMutableList().also { it[index] = nextColor })
                    },
                    onDelete = { onColorsChange(colors.filterIndexed { colorIndex, _ -> colorIndex != index }) },
                )
            }
            if (colors.size < maxColors) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onColorsChange(colors + "#808080") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add color",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditablePaletteSwatch(
    color: String,
    onColorChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember(color, expanded) { mutableStateOf(color.uppercase()) }
    val isValid = IDEOGRAM_HEX_COLOR.matches(draft)
    val density = LocalDensity.current

    Box {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(ideogramComposeColor(color) ?: MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), CircleShape)
                .clickable { expanded = true },
        )
        if (expanded) {
            Popup(
                popupPositionProvider = DropdownPositionProvider(with(density) { 4.dp.roundToPx() }),
                onDismissRequest = {
                    if (isValid && draft != color.uppercase()) onColorChange(draft)
                    expanded = false
                },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier.width(160.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { value ->
                                draft = normalizeHexDraft(value)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isValid) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(5.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                        DeskSubtleTextButton(
                            icon = Icons.Default.Delete,
                            text = "Remove color",
                            onClick = {
                                expanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

private val IDEOGRAM_HEX_COLOR = Regex("^#[0-9A-F]{6}$")

private fun ideogramComposeColor(value: String): Color? {
    if (!IDEOGRAM_HEX_COLOR.matches(value.uppercase())) return null
    val rgb = value.removePrefix("#").toIntOrNull(16) ?: return null
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
    )
}

private fun normalizeHexDraft(value: String): String {
    val digits = value
        .trim()
        .removePrefix("#")
        .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
        .uppercase()
        .take(6)
    return "#$digits"
}

private fun compositionBboxLabel(values: List<Int>): String {
    val bbox = normalizeCompositionBbox(values) ?: return "No bounding box"
    return "x ${bbox[1]}, y ${bbox[0]} | w ${bbox[3] - bbox[1]}, h ${bbox[2] - bbox[0]}"
}

private fun generationPromptReady(state: GenerationUiState): Boolean {
    return when (activePromptMode(state)) {
        ImagePromptMode.Text -> state.prompt.isNotBlank()
        ImagePromptMode.Json -> state.ideogram.jsonPrompt.isNotBlank() && state.ideogram.isJsonValid
    }
}

private fun isJsonPromptPreset(state: GenerationUiState): Boolean =
    activePromptMode(state) == ImagePromptMode.Json

private fun activePromptMode(state: GenerationUiState): ImagePromptMode =
    state.presets.firstOrNull { it.id == state.selectedPresetId }?.promptMode ?: ImagePromptMode.Text

@Composable
internal fun GenerationParameterControls(
    state: GenerationUiState,
    samplerOptions: List<String>,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
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
            DeskSubtleTextButton(
                icon = Icons.Default.RestartAlt,
                text = "Reset to defaults",
                onClick = onResetToPresetDefaults,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeskCompactNumberField("Steps", state.steps, onStepsChange, Modifier.weight(0.8f).widthIn(min = 104.dp), step = 1.0, minValue = 1.0)
        DeskCompactNumberField("Seed", state.seed, onSeedChange, Modifier.weight(1.1f).widthIn(min = 126.dp), step = 1.0)
        DeskCompactIconButton(
            icon = Icons.Default.Casino,
            contentDescription = "Random seed",
            onClick = onRandomizeSeed,
        )
        DeskCompactIconButton(
            icon = Icons.Default.Recycling,
            contentDescription = "Reuse last seed",
            onClick = onReuseLastSeed,
            enabled = state.history.any { it.usedSeed != null },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeskCompactNumberField("Width", state.width, onWidthChange, Modifier.weight(1f).widthIn(min = 126.dp), step = 16.0, minValue = 64.0)
        DeskCompactIconButton(
            icon = Icons.Default.SwapHoriz,
            contentDescription = "Swap dimensions",
            onClick = onSwapDimensions,
        )
        DeskCompactNumberField("Height", state.height, onHeightChange, Modifier.weight(1f).widthIn(min = 126.dp), step = 16.0, minValue = 64.0)
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
        DeskCompactNumberField("CFG", state.cfgScale, onCfgScaleChange, Modifier.weight(1f).widthIn(min = 118.dp), step = 0.1, minValue = 0.0, decimalPlaces = 1)
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
    showCompositionOverlay: Boolean,
    onShowCompositionOverlayChange: (Boolean) -> Unit,
    onUseImageAsCompositionReferenceChange: (Boolean) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
    onCompositionBboxEditStart: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
    onCompositionBboxEditEnd: () -> Unit,
    onCompositionBboxEditCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isJsonPromptPreset(state)) {
            CompositionPreviewHost(
                state = state,
                outputDir = outputDir,
                showCompositionOverlay = showCompositionOverlay,
                onShowCompositionOverlayChange = onShowCompositionOverlayChange,
                onUseImageAsCompositionReferenceChange = onUseImageAsCompositionReferenceChange,
                onCompositionElementSelected = onCompositionElementSelected,
                onCompositionBboxEditStart = onCompositionBboxEditStart,
                onCompositionBboxChange = onCompositionBboxChange,
                onCompositionBboxEditEnd = onCompositionBboxEditEnd,
                onCompositionBboxEditCancel = onCompositionBboxEditCancel,
            )
        } else {
            GeneratedImageGrid(state, outputDir)
        }
    }
}

@Composable
internal fun AnalyzeCompositionScreen(
    state: GenerationUiState,
    outputDir: String,
    showCompositionOverlay: Boolean,
    onCaptureModeChange: (CaptureImageMode) -> Unit,
    onCaptureUploadSelected: (File) -> Unit,
    onStartImageCapture: () -> Unit,
    onApplyImageCapture: () -> Unit,
    onAddAnalyzeElementBox: (String) -> Unit,
    onAnalyzeSelectedElementBox: () -> Unit,
    onAnalyzeAllElementBoxes: () -> Unit,
    onCompositionMutation: (CompositionMutation) -> Unit,
    onRunCompositionAction: (CompositionAction) -> Unit,
    onStartOverComposition: () -> Unit,
    onUndoComposition: () -> Unit,
    onRedoComposition: () -> Unit,
    onCompositionBboxEditStart: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
    onCompositionBboxEditEnd: () -> Unit,
    onCompositionBboxEditCancel: () -> Unit,
    onCompositionDescriptionChange: (Int, String) -> Unit,
    onCompositionTextChange: (Int, String) -> Unit,
    onCompositionPaletteChange: (Int, List<String>) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
) {
    var highlightStyleFields by remember { mutableStateOf(false) }
    var highlightCompositionFields by remember { mutableStateOf(false) }
    var highlightHighLevelDescription by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(DeskScreenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        ) {
            Column(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DeskPanelCornerRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(DeskPanelPadding),
                    verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
                ) {
                    Text(
                        text = "Analyze Composition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    AnalyzeImagePanel(
                        state = state,
                        onModeChange = onCaptureModeChange,
                        onUploadSelected = onCaptureUploadSelected,
                        onStartCapture = onStartImageCapture,
                        onApplyCapture = onApplyImageCapture,
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(DeskPanelCornerRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                CompositionPreviewHost(
                    state = state,
                    outputDir = outputDir,
                    showCompositionOverlay = true,
                    onShowCompositionOverlayChange = {},
                    onUseImageAsCompositionReferenceChange = {},
                    onCompositionElementSelected = onCompositionElementSelected,
                    onCompositionBboxEditStart = onCompositionBboxEditStart,
                    onCompositionBboxChange = onCompositionBboxChange,
                    onCompositionBboxEditEnd = onCompositionBboxEditEnd,
                    onCompositionBboxEditCancel = onCompositionBboxEditCancel,
                )
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
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
                Text(
                    text = "Structured Ideogram JSON",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.ideogram.document == null) {
                    Text(
                        text = "Choose or upload an image, then run Capture image details to create an editable Ideogram composition.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AnalyzeElementTools(
                        state = state,
                        onAddElementBox = onAddAnalyzeElementBox,
                        onAnalyzeSelectedElementBox = onAnalyzeSelectedElementBox,
                        onAnalyzeAllElementBoxes = onAnalyzeAllElementBoxes,
                    )
                    IdeogramCompositionForm(
                        state = state,
                        onMutation = onCompositionMutation,
                        onRunAction = onRunCompositionAction,
                        onStartOver = onStartOverComposition,
                        onUndo = onUndoComposition,
                        onRedo = onRedoComposition,
                        onCompositionDescriptionChange = onCompositionDescriptionChange,
                        onCompositionTextChange = onCompositionTextChange,
                        onCompositionPaletteChange = onCompositionPaletteChange,
                        onCompositionElementSelected = onCompositionElementSelected,
                        highlightStyleFields = highlightStyleFields,
                        highlightCompositionFields = highlightCompositionFields,
                        highlightHighLevelDescription = highlightHighLevelDescription,
                        onHighlightStyleFieldsChange = { highlightStyleFields = it },
                        onHighlightCompositionFieldsChange = { highlightCompositionFields = it },
                        onHighlightHighLevelDescriptionChange = { highlightHighLevelDescription = it },
                        showDocumentActions = false,
                        showAiActions = false,
                        showAdditionalFields = false,
                        showGenerateElementActions = false,
                        showElementAiActions = false,
                    )
                }
            }
        }
        }
        if (state.ideogramCapture.isBusy) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun AnalyzeElementTools(
    state: GenerationUiState,
    onAddElementBox: (String) -> Unit,
    onAnalyzeSelectedElementBox: () -> Unit,
    onAnalyzeAllElementBoxes: () -> Unit,
) {
    val document = state.ideogram.document
    val selectedIndex = state.selectedCompositionElementIndex
    val selectedElement = document?.elements?.getOrNull(selectedIndex)
    val canEdit = document != null && !state.ideogramCapture.isBusy
    val canAnalyze = canEdit &&
        state.ideogramCapture.sourceImage != null &&
        selectedElement?.bbox != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeskSubtleTextButton(
                icon = Icons.Default.AddBox,
                text = "Add object box",
                onClick = { onAddElementBox("obj") },
                enabled = canEdit,
            )
            DeskSubtleTextButton(
                icon = Icons.Default.TextFields,
                text = "Add text box",
                onClick = { onAddElementBox("text") },
                enabled = canEdit,
            )
            DeskButton(
                onClick = onAnalyzeSelectedElementBox,
                enabled = canAnalyze,
                modifier = Modifier.height(34.dp),
            ) {
                ButtonContent(
                    icon = Icons.Default.CenterFocusStrong,
                    text = if (state.ideogramCapture.isRefining) "Analyzing..." else "Analyze selected box",
                )
            }
        }
        Text(
            text = when {
                state.ideogramCapture.sourceImage == null -> "Choose an image source before analyzing a selected box."
                selectedElement == null -> "Select an element or add a box to analyze it."
                else -> "Selected element ${selectedIndex + 1}: move or resize its box in the preview, then analyze it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    ImageContextMenuArea(
        images = listOf(image.toImageContextMenuData(outputDir, fallbackFileName = "generated-image-${index + 1}.png")),
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
            image.generationTime?.let { seconds ->
                Text(
                    text = formatProgressDuration(seconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
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

@Composable
private fun CompactGenerationProgress(
    state: GenerationUiState,
    modifier: Modifier = Modifier,
) {
    val overallProgress = generationOverallProgress(state)
    val progressPercent = (overallProgress * 100f).roundToInt().coerceIn(0, 99)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.progressPhase.ifBlank { "Generating..." },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        DeskProgressTrack(
            progress = overallProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.progressSteps > 0) {
                    "Step ${state.progressStep} / ${state.progressSteps}"
                } else {
                    state.progressMessage.ifBlank { "Starting..." }
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(formatProgressDuration(state.progressTime))
                    if (state.progressEtaSeconds > 0) {
                        append("  /  ~")
                        append(formatProgressDuration(state.progressEtaSeconds.toDouble()))
                        append(" left")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun generationOverallProgress(state: GenerationUiState): Float {
    return state.progressOverall.coerceIn(0.02f, if (state.isGenerating) 0.99f else 1f)
}

private data class CompositionProgressInfo(
    val step: StagedIdeogramStep,
    val stepIndex: Int,
    val progress: Float,
    val percent: Int,
    val detail: String,
)

private fun compositionProgressInfo(state: GenerationUiState): CompositionProgressInfo {
    val step = state.ideogram.stagedStep ?: StagedIdeogramStep.SceneAndStyle
    val stepIndex = StagedIdeogramStep.entries.indexOf(step).coerceAtLeast(0)
    val elementFraction = if (step == StagedIdeogramStep.ElementDetails) {
        val count = state.ideogram.stagedElementCount ?: 0
        val index = state.ideogram.stagedElementIndex ?: 0
        if (count > 0) index.toFloat() / count.toFloat() else 0f
    } else 0f
    val progress = ((stepIndex + elementFraction) / StagedIdeogramStep.entries.size.toFloat()).coerceIn(0.02f, 0.99f)
    val percent = (progress * 100f).roundToInt().coerceIn(0, 99)
    val detail = if (step == StagedIdeogramStep.ElementDetails && state.ideogram.stagedElementCount != null) {
        "${step.label}: element ${state.ideogram.stagedElementIndex ?: 0} / ${state.ideogram.stagedElementCount}"
    } else {
        "${step.label}: stage ${stepIndex + 1} / ${StagedIdeogramStep.entries.size}"
    }
    return CompositionProgressInfo(step, stepIndex, progress, percent, detail)
}

@Composable
private fun CompactCompositionProgress(
    state: GenerationUiState,
    modifier: Modifier = Modifier,
) {
    val composition = compositionProgressInfo(state)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = composition.step.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${composition.percent}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        DeskProgressTrack(
            progress = composition.progress,
            modifier = Modifier.fillMaxWidth().height(5.dp),
        )
        Text(
            text = composition.detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompositionPreviewHost(
    state: GenerationUiState,
    outputDir: String,
    showCompositionOverlay: Boolean,
    onShowCompositionOverlayChange: (Boolean) -> Unit,
    onUseImageAsCompositionReferenceChange: (Boolean) -> Unit,
    onCompositionElementSelected: (Int) -> Unit,
    onCompositionBboxEditStart: () -> Unit,
    onCompositionBboxChange: (Int, List<Int>) -> Unit,
    onCompositionBboxEditEnd: () -> Unit,
    onCompositionBboxEditCancel: () -> Unit,
) {
    val image = state.ideogramCapture.sourceImage?.image
        ?: state.images.firstOrNull().takeUnless { state.isCurrentDraftResolutionModified }
    val elements = ideogramElementPreviews(state.ideogram.jsonPrompt)
    var showGrid by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (elements.isEmpty() && image == null) {
            Text("No valid composition to preview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IdeogramCompositionCanvas(
                elements = elements,
                elementIds = state.compositionElementIds,
                width = state.width.toIntOrNull() ?: 1024,
                height = state.height.toIntOrNull() ?: 1024,
                selectedIndex = state.selectedCompositionElementIndex,
                image = image,
                showOverlay = showCompositionOverlay,
                showGrid = showGrid,
                outputDir = outputDir,
                onElementSelected = onCompositionElementSelected,
                onBboxEditStart = onCompositionBboxEditStart,
                onElementBboxChange = onCompositionBboxChange,
                onBboxEditEnd = onCompositionBboxEditEnd,
                onBboxEditCancel = onCompositionBboxEditCancel,
                editingEnabled = !state.ideogramCapture.isBusy,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (image != null || elements.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(DeskControlCornerRadius)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (image != null) {
                    DeskInlineToggle(
                        label = "Use image for LLM improvements",
                        checked = state.useImageAsCompositionReference,
                        onCheckedChange = onUseImageAsCompositionReferenceChange,
                    )
                    DeskInlineToggle(
                        label = "Composition",
                        checked = showCompositionOverlay,
                        onCheckedChange = onShowCompositionOverlayChange,
                    )
                }
                DeskInlineToggle(
                    label = "Grid",
                    checked = showGrid,
                    onCheckedChange = { showGrid = it },
                )
            }
        }
        if (state.ideogram.isGeneratingJson) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { it.scrollDelta == Offset.Zero }) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            )
        }
    }
}

@Composable
internal fun ActionBar(
    state: GenerationUiState,
    backendState: BackendUiState,
    isTop: Boolean,
    generateEnabled: Boolean,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit,
    onToggleEndless: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
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
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DeskGroupSpacing),
            ) {
                DeskButton(
                    onClick = onGenerate,
                    enabled = generateEnabled,
                    modifier = Modifier
                        .height(44.dp)
                        .width(if (compact) 176.dp else 216.dp),
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
                if (state.isGenerating) {
                    DeskButton(
                        onClick = onCancelGeneration,
                        enabled = !state.isCancelling,
                        modifier = Modifier.height(44.dp).width(if (compact) 118.dp else 136.dp),
                    ) {
                        ButtonContent(
                            icon = Icons.Default.Stop,
                            text = if (state.isCancelling) "Cancelling..." else "Stop",
                        )
                    }
                }

                DeskHistoryStepper(
                    label = if (state.history.isEmpty()) "0 / 0" else "${state.historyIndex + 1} / ${state.history.size}",
                    onPrevious = onGoBack,
                    previousEnabled = state.canGoBack,
                    onNext = onGoForward,
                    nextEnabled = state.canGoForward,
                )

                DeskToolbarIconButton(
                    icon = Icons.Default.Repeat,
                    contentDescription = "Endless generation",
                    onClick = onToggleEndless,
                    selected = state.isEndless,
                )

                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                PresetActionControl(
                    state = state,
                    backendState = backendState,
                    onPresetSelected = onPresetSelected,
                    modifier = Modifier.width(if (compact) 210.dp else 280.dp),
                )

                Spacer(Modifier.weight(1f))

                when {
                    state.isGenerating -> CompactGenerationProgress(
                        state = state,
                        modifier = Modifier.width(if (compact) 210.dp else 280.dp),
                    )
                    state.ideogram.isGeneratingJson -> CompactCompositionProgress(
                        state = state,
                        modifier = Modifier.width(if (compact) 210.dp else 280.dp),
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
    val dotTone = when {
        state.presetLoadFailed -> DeskStatusTone.Error
        state.isLoadingPreset || state.isLoadingPresets -> DeskStatusTone.Warning
        backendState.status == BackendStatus.Ready && selectedPreset?.id == state.loadedPresetId -> DeskStatusTone.Success
        else -> DeskStatusTone.Neutral
    }
    DeskStatusDropdownField(
        value = selectedPreset?.name ?: "No preset",
        detail = selectedPreset?.promptMode?.displayName.orEmpty(),
        tone = dotTone,
        options = state.presets.map { it.name },
        onValueChange = { name ->
            state.presets.firstOrNull { it.name == name }?.let { preset ->
                onPresetSelected(preset.id)
            }
        },
        enabled = state.presets.isNotEmpty(),
        modifier = modifier
    )
}

@Composable
private fun SamplerMenu(
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskCompactDropdownField(
        label = "Sampler",
        value = value,
        options = options,
        onValueChange = onChange,
        modifier = modifier,
        minMenuWidth = 180.dp,
    )
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
    DeskCompactDropdownField(
        label = "AR",
        value = aspectRatioLabel(width, height),
        options = ratios.map { it.first },
        onValueChange = { selected ->
            val ratio = ratios.first { it.first == selected }.second
            onApplyAspectRatio(ratio.first, ratio.second)
        },
        modifier = modifier,
        minMenuWidth = 120.dp,
    )
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

private fun chooseCaptureImageFile(): File? {
    val dialog = FileDialog(null as Frame?, "Choose image for capture", FileDialog.LOAD).apply {
        isVisible = true
    }
    val file = dialog.file ?: return null
    val directory = dialog.directory ?: return null
    return File(directory, file)
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
