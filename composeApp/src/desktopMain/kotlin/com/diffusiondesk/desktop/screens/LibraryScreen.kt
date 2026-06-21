package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.viewmodel.ImagePresetForm
import com.diffusiondesk.desktop.viewmodel.LibraryMode
import com.diffusiondesk.desktop.viewmodel.LibraryUiState
import com.diffusiondesk.desktop.viewmodel.LlmPresetForm
import org.jetbrains.jewel.ui.component.Text

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    backendState: BackendUiState,
    selectedPresetId: String,
    samplerOptions: List<String>,
    onCreatePreset: () -> Unit,
    onEditPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onReloadPresets: () -> Unit,
    onShowImagePresets: () -> Unit,
    onShowLlmPresets: () -> Unit,
    onCancelEditor: () -> Unit,
    onFormChange: (ImagePresetForm) -> Unit,
    onLlmFormChange: (LlmPresetForm) -> Unit,
    onSavePreset: () -> Unit,
    onCreateLlmPreset: () -> Unit,
    onEditLlmPreset: (String) -> Unit,
    onDeleteLlmPreset: (String) -> Unit,
    onSaveLlmPreset: () -> Unit,
) {
    when (state.mode) {
        LibraryMode.List -> ImagePresetLibrary(
            state = state,
            backendState = backendState,
            selectedPresetId = selectedPresetId,
            onCreatePreset = onCreatePreset,
            onEditPreset = onEditPreset,
            onDeletePreset = onDeletePreset,
            onLoadPreset = onLoadPreset,
            onReloadPresets = onReloadPresets,
            onShowImagePresets = onShowImagePresets,
            onShowLlmPresets = onShowLlmPresets,
        )
        LibraryMode.Editor -> ImagePresetEditorPage(
            state = state,
            samplerOptions = samplerOptions,
            onCancelEditor = onCancelEditor,
            onFormChange = onFormChange,
            onSavePreset = onSavePreset,
        )
        LibraryMode.LlmList -> LlmPresetLibrary(
            state = state,
            onReloadPresets = onReloadPresets,
            onShowImagePresets = onShowImagePresets,
            onShowLlmPresets = onShowLlmPresets,
            onCreateLlmPreset = onCreateLlmPreset,
            onEditLlmPreset = onEditLlmPreset,
            onDeleteLlmPreset = onDeleteLlmPreset,
        )
        LibraryMode.LlmEditor -> LlmPresetEditorPage(
            state = state,
            onCancelEditor = onCancelEditor,
            onFormChange = onLlmFormChange,
            onSavePreset = onSaveLlmPreset,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagePresetLibrary(
    state: LibraryUiState,
    backendState: BackendUiState,
    selectedPresetId: String,
    onCreatePreset: () -> Unit,
    onEditPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onReloadPresets: () -> Unit,
    onShowImagePresets: () -> Unit,
    onShowLlmPresets: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DeskScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
    ) {
        LibraryTabHeader(
            selected = "image",
            onShowImagePresets = onShowImagePresets,
            onShowLlmPresets = onShowLlmPresets,
            actions = {
                DeskButton(onClick = onReloadPresets) {
                    Text("Refresh")
                }
                DeskButton(onClick = onCreatePreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(DeskIconSize))
                        Text("New Preset")
                    }
                }
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.presets.isEmpty()) {
                EmptyLibrary(onCreatePreset)
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                    verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                    maxItemsInEachRow = 3,
                ) {
                    state.presets.forEach { preset ->
                        ImagePresetCard(
                            preset = preset,
                            selected = preset.id == selectedPresetId,
                            canLoad = backendState.status == BackendStatus.Ready,
                            onLoad = { onLoadPreset(preset.id) },
                            onEdit = { onEditPreset(preset.id) },
                            onDelete = { onDeletePreset(preset.id) },
                        )
                    }
                }
            }
        }

        StatusMessages(state.message, state.error)
    }
}

@Composable
private fun LibraryTabHeader(
    selected: String,
    onShowImagePresets: () -> Unit,
    onShowLlmPresets: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    DeskTabHeader(
        tabs = listOf(
            DeskTabItem(
                selected = selected == "image",
                icon = Icons.Default.Tune,
                label = "Image Presets",
                onClick = onShowImagePresets,
            ),
            DeskTabItem(
                selected = selected == "llm",
                icon = Icons.Default.TextFields,
                label = "LLM Presets",
                onClick = onShowLlmPresets,
            ),
        ),
        actions = actions,
    )
}

@Composable
private fun EmptyLibrary(onCreatePreset: () -> Unit) {
    DeskEmptyState(
        title = "No image presets yet.",
        actionText = "Create Image Preset",
        onAction = onCreatePreset,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ImagePresetCard(
    preset: ImagePreset,
    selected: Boolean,
    canLoad: Boolean,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DeskPanel(
        modifier = Modifier
            .widthIn(min = 310.dp, max = 440.dp)
            .heightIn(min = 162.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PresetPathLine(
                    icon = if (preset.promptMode == ImagePromptMode.Json) Icons.Default.Code else Icons.Default.TextFields,
                    path = "Prompt: ${preset.promptMode.displayName}",
                )
                PresetPathLine(Icons.Default.Memory, preset.diffusionModel)
                if (preset.vae.isNotBlank()) PresetPathLine(Icons.Default.Palette, preset.vae)
                if (preset.t5xxl.isNotBlank()) PresetPathLine(Icons.Default.TextFields, preset.t5xxl)
                if (preset.llm.isNotBlank()) PresetPathLine(Icons.Default.TextFields, preset.llm)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    DeskStatusBadge(text = "Selected", tone = DeskStatusTone.Info)
                } else {
                    DeskButton(
                        onClick = onLoad,
                        enabled = canLoad,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Load")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeskIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit preset",
                        onClick = onEdit,
                    )
                    DeskIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete preset",
                        onClick = onDelete,
                        destructive = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetPathLine(icon: ImageVector, path: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = path.substringAfterLast('/').substringAfterLast('\\'),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmPresetLibrary(
    state: LibraryUiState,
    onReloadPresets: () -> Unit,
    onShowImagePresets: () -> Unit,
    onShowLlmPresets: () -> Unit,
    onCreateLlmPreset: () -> Unit,
    onEditLlmPreset: (String) -> Unit,
    onDeleteLlmPreset: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DeskScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
    ) {
        LibraryTabHeader(
            selected = "llm",
            onShowImagePresets = onShowImagePresets,
            onShowLlmPresets = onShowLlmPresets,
            actions = {
                DeskButton(onClick = onReloadPresets) {
                    Text("Refresh")
                }
                DeskButton(onClick = onCreateLlmPreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(DeskIconSize))
                        Text("New LLM")
                    }
                }
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.llmPresets.isEmpty()) {
                DeskEmptyState(
                    title = "No LLM presets yet.",
                    actionText = "Create LLM Preset",
                    onAction = onCreateLlmPreset,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                    verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                    maxItemsInEachRow = 3,
                ) {
                    state.llmPresets.forEach { preset ->
                        LlmPresetCard(
                            preset = preset,
                            onEdit = { onEditLlmPreset(preset.id) },
                            onDelete = { onDeleteLlmPreset(preset.id) },
                        )
                    }
                }
            }
        }

        StatusMessages(state.message, state.error)
    }
}

@Composable
private fun LlmPresetCard(
    preset: LlmPreset,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DeskPanel(
        modifier = Modifier
            .widthIn(min = 310.dp, max = 440.dp)
            .heightIn(min = 142.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PresetPathLine(Icons.Default.TextFields, preset.modelPath)
            if (preset.mmprojPath.isNotBlank()) {
                PresetPathLine(Icons.Default.Palette, preset.mmprojPath)
            }
            Text(
                text = "Placement: ${preset.placement.name.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preset.advancedArgs.isNotBlank()) {
                Text(
                    text = preset.advancedArgs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DeskIconButton(Icons.Default.Edit, "Edit LLM preset", onEdit)
                Spacer(Modifier.width(4.dp))
                DeskIconButton(Icons.Default.Delete, "Delete LLM preset", onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun LlmPresetEditorPage(
    state: LibraryUiState,
    onCancelEditor: () -> Unit,
    onFormChange: (LlmPresetForm) -> Unit,
    onSavePreset: () -> Unit,
) {
    val llmOptions = modelOptionsFor(state.modelSuggestions, "llm", "text-encoder", "text_encoders")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DeskScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap), verticalAlignment = Alignment.CenterVertically) {
                DeskIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onCancelEditor,
                )
                Column {
                    Text(
                        text = if (state.isEditingLlm) "Edit LLM Preset" else "New LLM Preset",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Keep the normal preset simple; use advanced args only when needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
                DeskButton(onClick = onCancelEditor) {
                    Text("Cancel")
                }
                DeskButton(onClick = onSavePreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Save Preset")
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
        ) {
            EditorSection("Preset") {
                LlmPresetCoreFields(
                    form = state.llmForm,
                    llmOptions = llmOptions,
                    projectorOptions = llmOptions,
                    onFormChange = onFormChange,
                )
            }
            EditorSection("Advanced llama.cpp Arguments") {
                LlmPresetAdvancedArgsField(state.llmForm, onFormChange)
            }
        }

        StatusMessages(state.message, state.error)
    }
}

@Composable
private fun ImagePresetEditorPage(
    state: LibraryUiState,
    samplerOptions: List<String>,
    onCancelEditor: () -> Unit,
    onFormChange: (ImagePresetForm) -> Unit,
    onSavePreset: () -> Unit,
) {
    val mainModelOptions = modelOptionsFor(state.modelSuggestions, "stable-diffusion", "diffusion_models", "unet", "root")
    val vaeOptions = modelOptionsFor(state.modelSuggestions, "vae")
    val textEncoderOptions = modelOptionsFor(state.modelSuggestions, "text-encoder", "text_encoders", "clip")
    val llmOptions = modelOptionsFor(state.modelSuggestions, "llm", "text-encoder", "text_encoders")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DeskScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap), verticalAlignment = Alignment.CenterVertically) {
                DeskIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onCancelEditor,
                )
                Column {
                    Text(
                        text = if (state.isEditing) "Edit Image Preset" else "New Image Preset",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Configure model files, default generation parameters, and device placement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
                DeskButton(onClick = onCancelEditor) {
                    Text("Cancel")
                }
                DeskButton(onClick = onSavePreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Save Preset")
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
        ) {
            EditorSection("Preset") {
                PresetLabeledField(
                    label = "Preset Name",
                    value = state.form.name,
                    onValueChange = { onFormChange(state.form.copy(name = it)) },
                    placeholder = "e.g. Z-Image Turbo Q8",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 980.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
                        verticalAlignment = Alignment.Top,
                    ) {
                        EditorSection("Model Components", modifier = Modifier.weight(1.15f)) {
                            ImagePresetModelComponentsFields(
                            form = state.form,
                            mainModelOptions = mainModelOptions,
                            vaeOptions = vaeOptions,
                            textEncoderOptions = textEncoderOptions,
                            llmOptions = llmOptions,
                            onFormChange = onFormChange,
                            )
                        }
                        EditorSection("Default Generation Parameters", modifier = Modifier.weight(0.85f)) {
                            ImagePresetGenerationParameterFields(
                            form = state.form,
                            samplerOptions = samplerOptions,
                            onFormChange = onFormChange,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
                    ) {
                        EditorSection("Model Components", modifier = Modifier.fillMaxWidth()) {
                            ImagePresetModelComponentsFields(
                            form = state.form,
                            mainModelOptions = mainModelOptions,
                            vaeOptions = vaeOptions,
                            textEncoderOptions = textEncoderOptions,
                            llmOptions = llmOptions,
                            onFormChange = onFormChange,
                            )
                        }
                        EditorSection("Default Generation Parameters", modifier = Modifier.fillMaxWidth()) {
                            ImagePresetGenerationParameterFields(
                            form = state.form,
                            samplerOptions = samplerOptions,
                            onFormChange = onFormChange,
                            )
                        }
                    }
                }
            }

            EditorSection("Performance & Memory") {
                ImagePresetPerformanceFields(state.form, onFormChange)
            }
        }

        StatusMessages(state.message, state.error)
    }
}

@Composable
private fun EditorSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DeskSection(title = title, modifier = modifier) {
        content()
    }
}
