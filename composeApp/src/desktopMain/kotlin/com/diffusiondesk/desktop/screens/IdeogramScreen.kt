package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.GenerationUiState
import com.diffusiondesk.desktop.viewmodel.IdeogramQualityPreset
import com.diffusiondesk.desktop.viewmodel.IdeogramStructureTab
import com.diffusiondesk.desktop.viewmodel.ideogramElementPreviews
import org.jetbrains.jewel.ui.component.DefaultButton as Button
import org.jetbrains.jewel.ui.component.Text

@Composable
fun IdeogramScreen(
    state: GenerationUiState,
    backendState: BackendUiState,
    samplerOptions: List<String>,
    onRawPromptChange: (String) -> Unit,
    onGenerateJson: () -> Unit,
    onJsonPromptChange: (String) -> Unit,
    onFormatJson: () -> Unit,
    onTabSelected: (IdeogramStructureTab) -> Unit,
    onQualityPresetSelected: (IdeogramQualityPreset) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onBatchCountChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onRandomizeSeed: () -> Unit,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onToggleParametersPanel: () -> Unit,
    onToggleStructurePanel: () -> Unit,
    actionBarPosition: String,
    outputDir: String,
) {
    val ideogram = state.ideogram
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
                generateEnabled = backendState.status == BackendStatus.Ready && state.ideogram.isJsonValid && state.ideogram.jsonPrompt.isNotBlank(),
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
            val compact = maxWidth < 1180.dp
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                ) {
                    CollapsiblePanel(
                        title = "Parameters",
                        icon = Icons.Default.AutoFixHigh,
                        collapsed = ideogram.panels.parametersCollapsed,
                        onToggle = onToggleParametersPanel,
                        collapsedHeight = 42.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (ideogram.panels.parametersCollapsed) 0.08f else 0.32f),
                    ) {
                        IdeogramParametersPanel(
                            state = state,
                            backendState = backendState,
                            samplerOptions = samplerOptions,
                            onRawPromptChange = onRawPromptChange,
                            onGenerateJson = onGenerateJson,
                            onQualityPresetSelected = onQualityPresetSelected,
                            onWidthChange = onWidthChange,
                            onHeightChange = onHeightChange,
                            onStepsChange = onStepsChange,
                            onCfgScaleChange = onCfgScaleChange,
                            onSeedChange = onSeedChange,
                            onBatchCountChange = onBatchCountChange,
                            onSamplerChange = onSamplerChange,
                            onRandomizeSeed = onRandomizeSeed,
                        )
                    }
                    CollapsiblePanel(
                        title = "Structure",
                        icon = Icons.Default.Code,
                        collapsed = ideogram.panels.structureCollapsed,
                        onToggle = onToggleStructurePanel,
                        collapsedHeight = 42.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (ideogram.panels.structureCollapsed) 0.08f else 0.38f),
                    ) {
                        IdeogramStructurePanel(
                            state = state,
                            onJsonPromptChange = onJsonPromptChange,
                            onFormatJson = onFormatJson,
                            onTabSelected = onTabSelected,
                        )
                    }
                    PreviewPanel(
                        state = state,
                        outputDir = outputDir,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.32f),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                ) {
                    CollapsiblePanel(
                        title = "Parameters",
                        icon = Icons.Default.AutoFixHigh,
                        collapsed = ideogram.panels.parametersCollapsed,
                        onToggle = onToggleParametersPanel,
                        collapsedWidth = 42.dp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (ideogram.panels.parametersCollapsed) 0.08f else 0.9f),
                    ) {
                        IdeogramParametersPanel(
                            state = state,
                            backendState = backendState,
                            samplerOptions = samplerOptions,
                            onRawPromptChange = onRawPromptChange,
                            onGenerateJson = onGenerateJson,
                            onQualityPresetSelected = onQualityPresetSelected,
                            onWidthChange = onWidthChange,
                            onHeightChange = onHeightChange,
                            onStepsChange = onStepsChange,
                            onCfgScaleChange = onCfgScaleChange,
                            onSeedChange = onSeedChange,
                            onBatchCountChange = onBatchCountChange,
                            onSamplerChange = onSamplerChange,
                            onRandomizeSeed = onRandomizeSeed,
                        )
                    }
                    CollapsiblePanel(
                        title = "Structure",
                        icon = Icons.Default.Code,
                        collapsed = ideogram.panels.structureCollapsed,
                        onToggle = onToggleStructurePanel,
                        collapsedWidth = 42.dp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (ideogram.panels.structureCollapsed) 0.08f else 1.15f),
                    ) {
                        IdeogramStructurePanel(
                            state = state,
                            onJsonPromptChange = onJsonPromptChange,
                            onFormatJson = onFormatJson,
                            onTabSelected = onTabSelected,
                        )
                    }
                    PreviewPanel(
                        state = state,
                        outputDir = outputDir,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                }
            }
        }

        if (!showActionBarOnTop) {
            ActionBar(
                state = state,
                backendState = backendState,
                isTop = false,
                generateEnabled = backendState.status == BackendStatus.Ready && state.ideogram.isJsonValid && state.ideogram.jsonPrompt.isNotBlank(),
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
private fun CollapsiblePanel(
    title: String,
    icon: ImageVector,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = 42.dp,
    collapsedHeight: Dp = 42.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = if (collapsed) {
            modifier
                .widthIn(min = collapsedWidth)
                .heightIn(min = collapsedHeight)
        } else {
            modifier
        },
        shape = RoundedCornerShape(DeskPanelCornerRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onToggle)
                    .padding(DeskCompactControlSpacing),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
                ) {
                    Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = title.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DeskPanelPadding),
                verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
            ) {
                PanelTitle(title, icon, onToggle)
                content()
            }
        }
    }
}

@Composable
private fun PanelTitle(
    title: String,
    icon: ImageVector,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        DeskIconButton(
            icon = Icons.Default.UnfoldLess,
            contentDescription = "Collapse $title",
            onClick = onToggle,
            tooltip = "Collapse $title",
        )
    }
}

@Composable
private fun IdeogramParametersPanel(
    state: GenerationUiState,
    backendState: BackendUiState,
    samplerOptions: List<String>,
    onRawPromptChange: (String) -> Unit,
    onGenerateJson: () -> Unit,
    onQualityPresetSelected: (IdeogramQualityPreset) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onBatchCountChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onRandomizeSeed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
    ) {
        DeskLabel("Raw Prompt")
        PaddedEditor(
            value = state.ideogram.rawPrompt,
            onValueChange = onRawPromptChange,
            minHeight = 126.dp,
            placeholder = "Describe the image normally; the LLM will convert this into Ideogram JSON.",
        )
        Button(
            onClick = onGenerateJson,
            enabled = state.ideogram.rawPrompt.isNotBlank() && !state.ideogram.isGeneratingJson,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            IdeogramButtonContent(
                icon = Icons.Default.AutoFixHigh,
                text = if (state.ideogram.isGeneratingJson) "Generating JSON..." else "Generate JSON",
            )
        }

        DeskLabel("Ideogram Quality")
        Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
            IdeogramQualityPreset.entries.forEach { preset ->
                QualityButton(
                    preset = preset,
                    selected = state.ideogram.qualityPreset == preset,
                    onClick = { onQualityPresetSelected(preset) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
            DeskTextField("Steps", state.steps, onStepsChange, Modifier.weight(1f))
            DeskTextField("Batch", state.batchCount, onBatchCountChange, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
            DeskTextField("Width", state.width, onWidthChange, Modifier.weight(1f))
            DeskTextField("Height", state.height, onHeightChange, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing), verticalAlignment = Alignment.Bottom) {
            DeskTextField("Seed", state.seed, onSeedChange, Modifier.weight(1f))
            DeskIconButton(Icons.Default.UnfoldMore, "Randomize seed", onRandomizeSeed, tooltip = "Randomize seed")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
            DeskTextField("CFG", state.cfgScale, onCfgScaleChange, Modifier.weight(1f))
            DeskDropdownField("Sampler", state.sampler, samplerOptions, onSamplerChange, Modifier.weight(1.35f))
        }

        Text(
            text = when (backendState.status) {
                BackendStatus.Ready -> "Image worker ready."
                BackendStatus.Starting -> "Image worker starting."
                BackendStatus.Error -> "Image worker error."
                BackendStatus.Stopped -> "Image worker stopped."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (backendState.status == BackendStatus.Ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualityButton(
    preset: IdeogramQualityPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DeskControlCornerRadius)
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = DeskSelectedContainerAlpha) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha)
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IdeogramStructurePanel(
    state: GenerationUiState,
    onJsonPromptChange: (String) -> Unit,
    onFormatJson: () -> Unit,
    onTabSelected: (IdeogramStructureTab) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
    ) {
        DeskTabHeader(
            tabs = listOf(
                DeskTabItem(
                    selected = state.ideogram.selectedTab == IdeogramStructureTab.Json,
                    icon = Icons.Default.Code,
                    label = "JSON",
                    onClick = { onTabSelected(IdeogramStructureTab.Json) },
                ),
                DeskTabItem(
                    selected = state.ideogram.selectedTab == IdeogramStructureTab.Preview,
                    icon = Icons.AutoMirrored.Filled.Article,
                    label = "Preview",
                    onClick = { onTabSelected(IdeogramStructureTab.Preview) },
                ),
            ),
        ) {
            DeskIconButton(Icons.Default.CheckCircle, "Format JSON", onFormatJson, tooltip = "Format JSON")
        }

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

        when (state.ideogram.selectedTab) {
            IdeogramStructureTab.Json -> PaddedEditor(
                value = state.ideogram.jsonPrompt,
                onValueChange = onJsonPromptChange,
                minHeight = 360.dp,
                monospace = true,
                modifier = Modifier.weight(1f),
            )
            IdeogramStructureTab.Preview -> IdeogramLayoutPreview(
                jsonPrompt = state.ideogram.jsonPrompt,
                width = state.width.toIntOrNull() ?: 1024,
                height = state.height.toIntOrNull() ?: 1024,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IdeogramLayoutPreview(
    jsonPrompt: String,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
) {
    val elements = ideogramElementPreviews(jsonPrompt)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 420.dp)
                .clip(RoundedCornerShape(DeskControlCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DeskControlCornerRadius)),
        ) {
            val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
            val canvasWidth = maxWidth
            val canvasHeight = (canvasWidth / aspect).coerceAtMost(maxHeight).coerceAtLeast(220.dp)
            Box(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(canvasHeight)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                elements.forEachIndexed { index, element ->
                    if (element.bbox.size == 4) {
                        val top = canvasHeight * (element.bbox[0] / 1000f)
                        val left = canvasWidth * (element.bbox[1] / 1000f)
                        val bottom = canvasHeight * (element.bbox[2] / 1000f)
                        val right = canvasWidth * (element.bbox[3] / 1000f)
                        val boxColor = if (element.type == "text") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .offset(left, top)
                                .width((right - left).coerceAtLeast(8.dp))
                                .height((bottom - top).coerceAtLeast(8.dp))
                                .border(2.dp, boxColor, RoundedCornerShape(2.dp))
                                .background(boxColor.copy(alpha = 0.10f))
                                .padding(4.dp),
                        ) {
                            Text(
                                text = if (element.type == "text" && element.text.isNotBlank()) element.text else "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = boxColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (elements.isEmpty()) {
            Text("No valid elements to preview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            elements.forEachIndexed { index, element ->
                ElementPreviewRow(index + 1, element.type, element.text, element.desc)
            }
        }
    }
}

@Composable
private fun ElementPreviewRow(index: Int, type: String, textValue: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = DeskSubtleSurfaceAlpha))
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

@Composable
private fun IdeogramButtonContent(
    icon: ImageVector,
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

@Composable
private fun PaddedEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp,
    placeholder: String = "",
    monospace: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DeskControlCornerRadius))
            .padding(9.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    if (value.isBlank() && placeholder.isNotBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
