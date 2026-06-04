package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.diffusiondesk.desktop.viewmodel.ImagePresetForm
import com.diffusiondesk.desktop.viewmodel.LibraryMode
import com.diffusiondesk.desktop.viewmodel.LibraryUiState
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
    onCancelEditor: () -> Unit,
    onFormChange: (ImagePresetForm) -> Unit,
    onSavePreset: () -> Unit,
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
        )
        LibraryMode.Editor -> ImagePresetEditorPage(
            state = state,
            samplerOptions = samplerOptions,
            onCancelEditor = onCancelEditor,
            onFormChange = onFormChange,
            onSavePreset = onSavePreset,
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Library Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Image presets are stored as JSON files in the DiffusionDesk app folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReloadPresets) {
                    Text("Refresh")
                }
                Button(onClick = onCreatePreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("New Preset")
                    }
                }
            }
        }

        LibraryTabHeader()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Image Generation Presets",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${state.presets.size} preset(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.presets.isEmpty()) {
                EmptyLibrary(onCreatePreset)
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
private fun LibraryTabHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text("Image Presets", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyLibrary(onCreatePreset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No image presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                LabeledField(
                    label = "UNet / Main Model (required)",
                    value = state.form.diffusionModel,
                    onValueChange = { onFormChange(state.form.copy(diffusionModel = it)) },
                    placeholder = "stable-diffusion/model.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
                TwoColumnRow {
                    LabeledField(
                        label = "VAE (optional)",
                        value = state.form.vae,
                        onValueChange = { onFormChange(state.form.copy(vae = it)) },
                        placeholder = "vae/ae.safetensors",
                        modifier = Modifier.weight(1f),
                    )
                    LabeledField(
                        label = "T5 / Text Encoder 2 (optional)",
                        value = state.form.t5xxl,
                        onValueChange = { onFormChange(state.form.copy(t5xxl = it)) },
                        placeholder = "text-encoder/t5.gguf",
                        modifier = Modifier.weight(1f),
                    )
                }
                TwoColumnRow {
                    LabeledField(
                        label = "LLM Text Encoder 3 (optional)",
                        value = state.form.llm,
                        onValueChange = { onFormChange(state.form.copy(llm = it)) },
                        placeholder = "text-encoder/qwen.gguf",
                        modifier = Modifier.weight(1f),
                    )
                    LabeledField(
                        label = "CLIP L (optional)",
                        value = state.form.clipL,
                        onValueChange = { onFormChange(state.form.copy(clipL = it)) },
                        placeholder = "clip/clip_l.gguf",
                        modifier = Modifier.weight(1f),
                    )
                }
                LabeledField(
                    label = "CLIP G (optional)",
                    value = state.form.clipG,
                    onValueChange = { onFormChange(state.form.copy(clipG = it)) },
                    placeholder = "clip/clip_g.gguf",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EditorSection("Default Generation Parameters") {
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
                    checked = state.form.clipOnCpu,
                    onCheckedChange = { onFormChange(state.form.copy(clipOnCpu = it)) },
                    title = "CLIP on CPU",
                    subtitle = "Keep text encoders on CPU to reserve GPU memory for the diffusion model.",
                )
                ToggleLine(
                    checked = state.form.vaeOnCpu,
                    onCheckedChange = { onFormChange(state.form.copy(vaeOnCpu = it)) },
                    title = "VAE on CPU",
                    subtitle = "Decode on CPU if VRAM pressure is high.",
                )
                ToggleLine(
                    checked = state.form.offloadParamsToCpu,
                    onCheckedChange = { onFormChange(state.form.copy(offloadParamsToCpu = it)) },
                    title = "Offload parameters to CPU",
                    subtitle = "Let the preset decide CPU/GPU placement instead of global VRAM logic.",
                )
                ToggleLine(
                    checked = state.form.flashAttention,
                    onCheckedChange = { onFormChange(state.form.copy(flashAttention = it)) },
                    title = "Flash attention",
                    subtitle = "Enable when supported by the selected model and backend build.",
                )
            }
        }

        StatusMessages(state.message, state.error)
    }
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
private fun ToggleLine(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
