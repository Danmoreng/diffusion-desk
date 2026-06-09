package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.core.LlmPlacement
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.core.ModelSummary
import com.diffusiondesk.desktop.viewmodel.ImagePresetForm
import com.diffusiondesk.desktop.viewmodel.LibraryMode
import com.diffusiondesk.desktop.viewmodel.LibraryUiState
import com.diffusiondesk.desktop.viewmodel.LlmPresetForm
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton as Button
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
                Button(onClick = onReloadPresets) {
                    Text("Refresh")
                }
                Button(onClick = onCreatePreset) {
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No image presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(DeskLayoutGap))
        Button(onClick = onCreatePreset) {
            Text("Create Image Preset")
        }
    }
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
                    StatusButton(
                        icon = Icons.Default.Check,
                        text = "Selected",
                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        content = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
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
                Button(onClick = onReloadPresets) {
                    Text("Refresh")
                }
                Button(onClick = onCreateLlmPreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(DeskIconSize))
                        Text("New LLM")
                    }
                }
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.llmPresets.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No LLM presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(DeskLayoutGap))
                    Button(onClick = onCreateLlmPreset) {
                        Text("Create LLM Preset")
                    }
                }
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
                Button(onClick = onCancelEditor) {
                    Text("Cancel")
                }
                Button(onClick = onSavePreset) {
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
                LabeledField(
                    label = "Preset Name",
                    value = state.llmForm.name,
                    onValueChange = { onFormChange(state.llmForm.copy(name = it)) },
                    placeholder = "e.g. Small Tagger CPU",
                    modifier = Modifier.fillMaxWidth(),
                )
                ModelPathField(
                    label = "Model Path",
                    value = state.llmForm.modelPath,
                    options = llmOptions,
                    onValueChange = { onFormChange(state.llmForm.copy(modelPath = it)) },
                    placeholder = "llm/model.gguf or D:\\models\\model.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
                ModelPathField(
                    label = "Vision Projector (optional)",
                    value = state.llmForm.mmprojPath,
                    options = llmOptions,
                    onValueChange = { onFormChange(state.llmForm.copy(mmprojPath = it)) },
                    placeholder = "llm/mmproj-model-f16.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
                DeskDropdownField(
                    label = "Placement",
                    value = state.llmForm.placement.name.uppercase(),
                    options = listOf("AUTO", "CPU", "GPU"),
                    onValueChange = {
                        onFormChange(
                            state.llmForm.copy(
                                placement = when (it) {
                                    "AUTO" -> LlmPlacement.Auto
                                    "GPU" -> LlmPlacement.Gpu
                                    else -> LlmPlacement.Cpu
                                },
                            ),
                        )
                    },
                    modifier = Modifier.widthIn(min = 180.dp, max = 220.dp),
                )
            }
            EditorSection("Advanced llama.cpp Arguments") {
                LabeledField(
                    label = "Arguments",
                    value = state.llmForm.advancedArgs,
                    onValueChange = { onFormChange(state.llmForm.copy(advancedArgs = it)) },
                    placeholder = "--ctx-size 4096 --threads 8",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
            }
        }

        StatusMessages(state.message, state.error)
    }
}

@Composable
private fun StatusButton(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = content)
        Text(text, color = content, fontWeight = FontWeight.SemiBold)
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
                Button(onClick = onCancelEditor) {
                    Text("Cancel")
                }
                Button(onClick = onSavePreset) {
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
                LabeledField(
                    label = "Preset Name",
                    value = state.form.name,
                    onValueChange = { onFormChange(state.form.copy(name = it)) },
                    placeholder = "e.g. Z-Image Turbo Q8",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EditorSection("Model Components") {
                ModelPathField(
                    label = "UNet / Main Model (required)",
                    value = state.form.diffusionModel,
                    options = mainModelOptions,
                    onValueChange = { onFormChange(state.form.copy(diffusionModel = it)) },
                    placeholder = "stable-diffusion/model.gguf or D:\\models\\model.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
                ModelPathField(
                    label = "Unconditional Diffusion Model (optional)",
                    value = state.form.uncondDiffusionModel,
                    options = mainModelOptions,
                    onValueChange = { onFormChange(state.form.copy(uncondDiffusionModel = it)) },
                    placeholder = "stable-diffusion/ideogram4_uncond-Q8_0.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
                TwoColumnRow {
                    ModelPathField(
                        label = "VAE (optional)",
                        value = state.form.vae,
                        options = vaeOptions,
                        onValueChange = { onFormChange(state.form.copy(vae = it)) },
                        placeholder = "vae/ae.safetensors",
                        modifier = Modifier.weight(1f),
                    )
                    ModelPathField(
                        label = "T5 / Text Encoder 2 (optional)",
                        value = state.form.t5xxl,
                        options = textEncoderOptions,
                        onValueChange = { onFormChange(state.form.copy(t5xxl = it)) },
                        placeholder = "text-encoder/t5.gguf",
                        modifier = Modifier.weight(1f),
                    )
                }
                TwoColumnRow {
                    ModelPathField(
                        label = "LLM Text Encoder 3 (optional)",
                        value = state.form.llm,
                        options = llmOptions,
                        onValueChange = { onFormChange(state.form.copy(llm = it)) },
                        placeholder = "text-encoder/qwen.gguf",
                        modifier = Modifier.weight(1f),
                    )
                    ModelPathField(
                        label = "CLIP L (optional)",
                        value = state.form.clipL,
                        options = textEncoderOptions,
                        onValueChange = { onFormChange(state.form.copy(clipL = it)) },
                        placeholder = "clip/clip_l.gguf",
                        modifier = Modifier.weight(1f),
                    )
                }
                ModelPathField(
                    label = "CLIP G (optional)",
                    value = state.form.clipG,
                    options = textEncoderOptions,
                    onValueChange = { onFormChange(state.form.copy(clipG = it)) },
                    placeholder = "clip/clip_g.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EditorSection("Default Generation Parameters") {
                PromptModeField(
                    value = state.form.promptMode,
                    onValueChange = { onFormChange(state.form.copy(promptMode = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TwoColumnRow {
                    LabeledField(
                        label = "Width",
                        value = state.form.defaultWidth,
                        onValueChange = { onFormChange(state.form.copy(defaultWidth = it)) },
                        placeholder = "1024",
                        modifier = Modifier.weight(1f),
                    )
                    LabeledField(
                        label = "Height",
                        value = state.form.defaultHeight,
                        onValueChange = { onFormChange(state.form.copy(defaultHeight = it)) },
                        placeholder = "1024",
                        modifier = Modifier.weight(1f),
                    )
                }
                TwoColumnRow {
                    LabeledField(
                        label = "Steps",
                        value = state.form.defaultSteps,
                        onValueChange = { onFormChange(state.form.copy(defaultSteps = it)) },
                        placeholder = "4",
                        modifier = Modifier.weight(1f),
                    )
                    LabeledField(
                        label = "CFG Scale",
                        value = state.form.defaultCfgScale,
                        onValueChange = { onFormChange(state.form.copy(defaultCfgScale = it)) },
                        placeholder = "1.0",
                        modifier = Modifier.weight(1f),
                    )
                }
                SamplerField(
                    value = state.form.defaultSampler,
                    options = samplerOptions,
                    onValueChange = { onFormChange(state.form.copy(defaultSampler = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledField(
                    label = "Default Negative Prompt",
                    value = state.form.defaultNegativePrompt,
                    onValueChange = { onFormChange(state.form.copy(defaultNegativePrompt = it)) },
                    placeholder = "deformed, blurry, low quality, watermark",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
            }

            EditorSection("Performance & Memory") {
                ToggleLine(
                    checked = state.form.flashAttention,
                    onCheckedChange = { onFormChange(state.form.copy(flashAttention = it)) },
                    title = "Flash attention",
                    subtitle = "Enable when supported by the selected model and backend build.",
                )
                ToggleLine(
                    checked = state.form.streamLayers,
                    onCheckedChange = { onFormChange(state.form.copy(streamLayers = it, offloadParamsToCpu = state.form.offloadParamsToCpu || it)) },
                    title = "Stream diffusion layers",
                    subtitle = "Stream diffusion layers through CPU memory. Parameter offload is enabled automatically.",
                )
                if (state.form.streamLayers) {
                    DeskDropdownField(
                        label = "VRAM Budget",
                        value = if (state.form.useGlobalVramBudget) "Global setting" else "Preset override",
                        options = listOf("Global setting", "Preset override"),
                        onValueChange = { selected ->
                            onFormChange(state.form.copy(useGlobalVramBudget = selected == "Global setting"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!state.form.useGlobalVramBudget) {
                        LabeledField(
                            label = "Preset VRAM Budget (GiB)",
                            value = state.form.maxVramGb,
                            onValueChange = { onFormChange(state.form.copy(maxVramGb = it)) },
                            placeholder = "12.0",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(
                    text = "Advanced CPU fallbacks",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleLine(
                    checked = state.form.clipOnCpu,
                    onCheckedChange = { onFormChange(state.form.copy(clipOnCpu = it)) },
                    title = "CLIP on CPU",
                    subtitle = "Reserve GPU memory when the text encoder does not fit alongside the diffusion model.",
                )
                ToggleLine(
                    checked = state.form.vaeOnCpu,
                    onCheckedChange = { onFormChange(state.form.copy(vaeOnCpu = it)) },
                    title = "VAE on CPU",
                    subtitle = "Fallback for high-resolution VAE decoding or VAE out-of-memory errors.",
                )
                if (!state.form.streamLayers) {
                    ToggleLine(
                        checked = state.form.offloadParamsToCpu,
                        onCheckedChange = { onFormChange(state.form.copy(offloadParamsToCpu = it)) },
                        title = "Offload parameters to CPU",
                        subtitle = "Keep model parameters in system memory when layer streaming is disabled.",
                    )
                }
            }
        }

        StatusMessages(state.message, state.error)
    }
}

private fun modelOptionsFor(models: List<ModelSummary>, vararg types: String): List<String> {
    val acceptedTypes = types.toSet()
    return models
        .asSequence()
        .filter { it.type in acceptedTypes }
        .map { it.id }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .toList()
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    DeskPanel(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun TwoColumnRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        content = content,
    )
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    DeskTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        singleLine = singleLine,
    )
}

@Composable
private fun ModelPathField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    DeskSearchableTextDropdownField(
        label = label,
        value = value,
        options = options,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
    )
}

@Composable
private fun SamplerField(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskDropdownField(
        label = "Sampler",
        value = value,
        options = options,
        onValueChange = onValueChange,
        modifier = modifier,
    )
}

@Composable
private fun PromptModeField(
    value: ImagePromptMode,
    onValueChange: (ImagePromptMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskDropdownField(
        label = "Prompt Mode",
        value = value.displayName,
        options = ImagePromptMode.values().map { it.displayName },
        onValueChange = { label ->
            onValueChange(ImagePromptMode.values().firstOrNull { it.displayName == label } ?: ImagePromptMode.Text)
        },
        modifier = modifier,
    )
}

@Composable
private fun ToggleLine(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusMessages(message: String, error: String?) {
    if (message.isNotBlank() || error != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
