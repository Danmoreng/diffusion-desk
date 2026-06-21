package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.viewmodel.SetupModelOption
import com.diffusiondesk.desktop.viewmodel.SetupUiState
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SetupScreen(
    state: SetupUiState,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onImagePresetNameChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onEnableLlmPresetChange: (Boolean) -> Unit,
    onLlmPresetNameChange: (String) -> Unit,
    onLlmModelChange: (String) -> Unit,
    onMmprojChange: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp),
        ) {
            val compact = maxWidth < 920.dp
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
                ) {
                    SetupHeader(state)
                    SetupBody(
                        state = state,
                        onModelDirChange = onModelDirChange,
                        onOutputDirChange = onOutputDirChange,
                        onScan = onScan,
                        onBack = onBack,
                        onImagePresetNameChange = onImagePresetNameChange,
                        onImageModelChange = onImageModelChange,
                        onEnableLlmPresetChange = onEnableLlmPresetChange,
                        onLlmPresetNameChange = onLlmPresetNameChange,
                        onLlmModelChange = onLlmModelChange,
                        onMmprojChange = onMmprojChange,
                        onFinish = onFinish,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
                ) {
                    SetupRail(
                        state = state,
                        modifier = Modifier.width(270.dp).fillMaxHeight(),
                    )
                    SetupBody(
                        state = state,
                        onModelDirChange = onModelDirChange,
                        onOutputDirChange = onOutputDirChange,
                        onScan = onScan,
                        onBack = onBack,
                        onImagePresetNameChange = onImagePresetNameChange,
                        onImageModelChange = onImageModelChange,
                        onEnableLlmPresetChange = onEnableLlmPresetChange,
                        onLlmPresetNameChange = onLlmPresetNameChange,
                        onLlmModelChange = onLlmModelChange,
                        onMmprojChange = onMmprojChange,
                        onFinish = onFinish,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupHeader(state: SetupUiState) {
    DeskPanel {
        Text(
            text = "Diffusion Desk Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (state.step == 1) {
                "Choose where models live and where generated images should be written."
            } else {
                "Create the starter presets used by Generate and local assistant tools."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupRail(
    state: SetupUiState,
    modifier: Modifier = Modifier,
) {
    DeskPanel(modifier = modifier) {
        Text(
            text = "Diffusion Desk Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "A short path through folders, presets, and the first Generate workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DeskSectionSpacing))
        SetupStepIndicator(
            number = "1",
            title = "Folders",
            subtitle = "Models and outputs",
            icon = Icons.Default.FolderOpen,
            selected = state.step == 1,
            complete = state.step > 1,
        )
        SetupStepIndicator(
            number = "2",
            title = "Presets",
            subtitle = "Image and optional LLM",
            icon = Icons.Default.Image,
            selected = state.step == 2,
            complete = false,
        )
        Spacer(Modifier.weight(1f))
        SetupStatLine("Image models", state.imageModels.size)
        SetupStatLine("LLM models", state.llmModels.size)
    }
}

@Composable
private fun SetupBody(
    state: SetupUiState,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onImagePresetNameChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onEnableLlmPresetChange: (Boolean) -> Unit,
    onLlmPresetNameChange: (String) -> Unit,
    onLlmModelChange: (String) -> Unit,
    onMmprojChange: (String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskPanel(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.step == 1) {
            FolderStep(
                state = state,
                onModelDirChange = onModelDirChange,
                onOutputDirChange = onOutputDirChange,
                onScan = onScan,
            )
        } else {
            PresetStep(
                state = state,
                onBack = onBack,
                onImagePresetNameChange = onImagePresetNameChange,
                onImageModelChange = onImageModelChange,
                onEnableLlmPresetChange = onEnableLlmPresetChange,
                onLlmPresetNameChange = onLlmPresetNameChange,
                onLlmModelChange = onLlmModelChange,
                onMmprojChange = onMmprojChange,
                onFinish = onFinish,
            )
        }
    }
}

@Composable
private fun FolderStep(
    state: SetupUiState,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onScan: () -> Unit,
) {
    Text(
        text = "Folders",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Use absolute paths when your model library is outside this repository.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupPathRow(
        label = "Model Directory",
        value = state.modelDir,
        placeholder = "/home/sebastian/StableDiffusion/models",
        onValueChange = onModelDirChange,
    )
    SetupPathRow(
        label = "Output Directory",
        value = state.outputDir,
        placeholder = "/home/sebastian/StableDiffusion/outputs",
        onValueChange = onOutputDirChange,
    )
    ExpectedFoldersPreview(state.modelDir)
    SetupMessage(state)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeskButton(
            onClick = onScan,
            enabled = state.canContinueFromFolders,
        ) {
            ButtonLabel(
                icon = {
                    if (state.isScanning) {
                        CircularProgressIndicator(Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                text = if (state.isScanning) "Scanning" else "Scan Models",
            )
        }
    }
}

@Composable
private fun PresetStep(
    state: SetupUiState,
    onBack: () -> Unit,
    onImagePresetNameChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onEnableLlmPresetChange: (Boolean) -> Unit,
    onLlmPresetNameChange: (String) -> Unit,
    onLlmModelChange: (String) -> Unit,
    onMmprojChange: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        text = "Starter Presets",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "The image preset is required. The LLM preset can be added later from Presets or System.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupPresetBlock(
        icon = Icons.Default.Image,
        title = "Image Generation",
        subtitle = "Used by the Generate screen for the first image.",
    ) {
        DeskTextField(
            label = "Preset Name",
            value = state.imagePresetName,
            onValueChange = onImagePresetNameChange,
            modifier = Modifier.fillMaxWidth(),
        )
        ModelDropdown(
            label = "Main Image Model",
            selected = state.selectedImageModel,
            models = state.imageModels,
            emptyText = "No image models found in stable-diffusion, diffusion_models, unet, or the model root.",
            onSelected = onImageModelChange,
        )
    }
    SetupPresetBlock(
        icon = Icons.Default.SmartToy,
        title = "Local LLM",
        subtitle = "Optional preset for assistant tools, prompt enhancement, and image tagging when a projector is selected.",
    ) {
        DeskCheckboxRow(
            checked = state.enableLlmPreset,
            onCheckedChange = onEnableLlmPresetChange,
            title = "Create LLM preset",
        )
        if (state.enableLlmPreset) {
            DeskTextField(
                label = "Preset Name",
                value = state.llmPresetName,
                onValueChange = onLlmPresetNameChange,
                modifier = Modifier.fillMaxWidth(),
            )
            ModelDropdown(
                label = "LLM Model",
                selected = state.selectedLlmModel,
                models = state.llmModels,
                emptyText = "No LLM models found in llm or text-encoder.",
                onSelected = onLlmModelChange,
            )
            ModelDropdown(
                label = "MMProj",
                selected = state.selectedMmproj,
                models = state.mmprojModels,
                emptyText = "No projector found. Leave blank for text-only models.",
                allowBlank = true,
                onSelected = onMmprojChange,
            )
        }
    }
    SetupMessage(state)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeskOutlinedButton(onClick = onBack) {
            ButtonLabel(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp)) },
                text = "Back",
            )
        }
        DeskButton(
            onClick = onFinish,
            enabled = state.canFinish,
        ) {
            ButtonLabel(
                icon = {
                    if (state.isFinishing) {
                        CircularProgressIndicator(Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                text = if (state.isFinishing) "Finishing" else "Finish Setup",
            )
        }
    }
}

@Composable
private fun SetupPathRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        verticalAlignment = Alignment.Bottom,
    ) {
        DeskTextField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
        )
        DeskOutlinedButton(
            onClick = { chooseFolder(value)?.let(onValueChange) },
            modifier = Modifier.widthIn(min = 92.dp),
        ) {
            ButtonLabel(
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                text = "Browse",
            )
        }
    }
}

@Composable
private fun ButtonLabel(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(text)
    }
}

@Composable
private fun ExpectedFoldersPreview(modelDir: String) {
    val root = modelDir.trim().ifBlank { "models" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeskLabel("Expected Model Folders")
        listOf("stable-diffusion", "diffusion_models", "unet", "vae", "llm", "lora", "esrgan").forEach { folder ->
            Text(
                text = File(root, folder).absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SetupPresetBlock(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskPanelCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            .padding(DeskPanelPadding),
        verticalArrangement = Arrangement.spacedBy(DeskControlSpacing),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun ModelDropdown(
    label: String,
    selected: String,
    models: List<SetupModelOption>,
    emptyText: String,
    allowBlank: Boolean = false,
    onSelected: (String) -> Unit,
) {
    if (models.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DeskLabel(label)
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val options = if (allowBlank) listOf("") + models.map { it.id } else models.map { it.id }
    DeskDropdownField(
        label = label,
        value = selected,
        options = options,
        onValueChange = onSelected,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SetupStepIndicator(
    number: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    complete: Boolean,
) {
    val color = when {
        complete -> deskStatusColor(DeskStatusTone.Success)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeskControlCornerRadius))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = DeskSelectedContainerAlpha) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (complete) Icons.Default.CheckCircle else icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column {
            Text("$number. $title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupStatLine(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SetupMessage(state: SetupUiState) {
    val error = state.error
    val message = state.message
    if (error.isNullOrBlank() && message.isBlank()) {
        return
    }
    Text(
        text = error ?: message,
        style = MaterialTheme.typography.bodySmall,
        color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

private fun chooseFolder(current: String): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        currentDirectory = File(current).takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}
