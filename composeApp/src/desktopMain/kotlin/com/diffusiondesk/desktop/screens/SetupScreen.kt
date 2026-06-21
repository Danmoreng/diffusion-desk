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
import com.diffusiondesk.desktop.viewmodel.ImagePresetForm
import com.diffusiondesk.desktop.viewmodel.LlmPresetForm
import com.diffusiondesk.desktop.viewmodel.SetupLlmRole
import com.diffusiondesk.desktop.viewmodel.SetupModelOption
import com.diffusiondesk.desktop.viewmodel.SetupUiState
import com.diffusiondesk.desktop.viewmodel.displayName
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
    onImagePresetFormChange: (ImagePresetForm) -> Unit,
    onContinueFromImagePreset: () -> Unit,
    onEnableTaggingLlmPresetChange: (Boolean) -> Unit,
    onTaggingLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onEnablePromptEnhancerLlmPresetChange: (Boolean) -> Unit,
    onPromptEnhancerLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onEnableAssistantLlmPresetChange: (Boolean) -> Unit,
    onAssistantLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onContinueLlmStep: (SetupLlmRole) -> Unit,
    onSkipLlmStep: (SetupLlmRole) -> Unit,
    onFinish: () -> Unit,
    onCancel: (() -> Unit)? = null,
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
                        onImagePresetFormChange = onImagePresetFormChange,
                        onContinueFromImagePreset = onContinueFromImagePreset,
                        onEnableTaggingLlmPresetChange = onEnableTaggingLlmPresetChange,
                        onTaggingLlmPresetFormChange = onTaggingLlmPresetFormChange,
                        onEnablePromptEnhancerLlmPresetChange = onEnablePromptEnhancerLlmPresetChange,
                        onPromptEnhancerLlmPresetFormChange = onPromptEnhancerLlmPresetFormChange,
                        onEnableAssistantLlmPresetChange = onEnableAssistantLlmPresetChange,
                        onAssistantLlmPresetFormChange = onAssistantLlmPresetFormChange,
                        onContinueLlmStep = onContinueLlmStep,
                        onSkipLlmStep = onSkipLlmStep,
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
                        onImagePresetFormChange = onImagePresetFormChange,
                        onContinueFromImagePreset = onContinueFromImagePreset,
                        onEnableTaggingLlmPresetChange = onEnableTaggingLlmPresetChange,
                        onTaggingLlmPresetFormChange = onTaggingLlmPresetFormChange,
                        onEnablePromptEnhancerLlmPresetChange = onEnablePromptEnhancerLlmPresetChange,
                        onPromptEnhancerLlmPresetFormChange = onPromptEnhancerLlmPresetFormChange,
                        onEnableAssistantLlmPresetChange = onEnableAssistantLlmPresetChange,
                        onAssistantLlmPresetFormChange = onAssistantLlmPresetFormChange,
                        onContinueLlmStep = onContinueLlmStep,
                        onSkipLlmStep = onSkipLlmStep,
                        onFinish = onFinish,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        onCancel?.let { cancel ->
            DeskOutlinedButton(
                onClick = cancel,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text("Close Setup")
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
                "Create the starter image preset and optional LLM presets."
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
            subtitle = "Image generation",
            icon = Icons.Default.Image,
            selected = state.step == 2,
            complete = state.step > 2,
        )
        SetupStepIndicator(
            number = "3",
            title = "Tagging",
            subtitle = "Optional vision LLM",
            icon = Icons.Default.SmartToy,
            selected = state.step == 3,
            complete = state.step > 3,
        )
        SetupStepIndicator(
            number = "4",
            title = "Enhancement",
            subtitle = "Optional prompt LLM",
            icon = Icons.Default.SmartToy,
            selected = state.step == 4,
            complete = state.step > 4,
        )
        SetupStepIndicator(
            number = "5",
            title = "Assistant",
            subtitle = "Optional chat LLM",
            icon = Icons.Default.SmartToy,
            selected = state.step == 5,
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
    onImagePresetFormChange: (ImagePresetForm) -> Unit,
    onContinueFromImagePreset: () -> Unit,
    onEnableTaggingLlmPresetChange: (Boolean) -> Unit,
    onTaggingLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onEnablePromptEnhancerLlmPresetChange: (Boolean) -> Unit,
    onPromptEnhancerLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onEnableAssistantLlmPresetChange: (Boolean) -> Unit,
    onAssistantLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onContinueLlmStep: (SetupLlmRole) -> Unit,
    onSkipLlmStep: (SetupLlmRole) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskPanel(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        when (state.step) {
            1 -> FolderStep(
                state = state,
                onModelDirChange = onModelDirChange,
                onOutputDirChange = onOutputDirChange,
                onScan = onScan,
            )
            2 -> ImagePresetStep(
                state = state,
                onBack = onBack,
                onImagePresetFormChange = onImagePresetFormChange,
                onContinue = onContinueFromImagePreset,
            )
            3 -> LlmRoleStep(
                state = state,
                role = SetupLlmRole.Tagging,
                enabled = state.enableTaggingLlmPreset,
                form = state.taggingLlmForm,
                onBack = onBack,
                onEnabledChange = onEnableTaggingLlmPresetChange,
                onFormChange = onTaggingLlmPresetFormChange,
                onSkip = { onSkipLlmStep(SetupLlmRole.Tagging) },
                onContinue = { onContinueLlmStep(SetupLlmRole.Tagging) },
            )
            4 -> LlmRoleStep(
                state = state,
                role = SetupLlmRole.PromptEnhancement,
                enabled = state.enablePromptEnhancerLlmPreset,
                form = state.promptEnhancerLlmForm,
                onBack = onBack,
                onEnabledChange = onEnablePromptEnhancerLlmPresetChange,
                onFormChange = onPromptEnhancerLlmPresetFormChange,
                onSkip = { onSkipLlmStep(SetupLlmRole.PromptEnhancement) },
                onContinue = { onContinueLlmStep(SetupLlmRole.PromptEnhancement) },
            )
            else -> LlmRoleStep(
                state = state,
                role = SetupLlmRole.Assistant,
                enabled = state.enableAssistantLlmPreset,
                form = state.assistantLlmForm,
                onBack = onBack,
                onEnabledChange = onEnableAssistantLlmPresetChange,
                onFormChange = onAssistantLlmPresetFormChange,
                onSkip = {
                    onSkipLlmStep(SetupLlmRole.Assistant)
                    onFinish()
                },
                onContinue = onFinish,
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
private fun ImagePresetStep(
    state: SetupUiState,
    onBack: () -> Unit,
    onImagePresetFormChange: (ImagePresetForm) -> Unit,
    onContinue: () -> Unit,
) {
    Text(
        text = "Image Preset",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "The starter image preset is required. LLM presets are optional in the next steps.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupPresetBlock(
        icon = Icons.Default.Image,
        title = "Image Generation",
        subtitle = "Starter defaults for Generate. You can refine every preset later from Presets.",
    ) {
        PresetLabeledField(
            label = "Preset Name",
            value = state.imageForm.name,
            onValueChange = { onImagePresetFormChange(state.imageForm.copy(name = it)) },
            placeholder = "e.g. Z-Image Turbo Q8",
            modifier = Modifier.fillMaxWidth(),
        )
        ImagePresetModelComponentsFields(
            form = state.imageForm,
            mainModelOptions = setupOptions(state.imageModels),
            vaeOptions = setupOptions(state.vaeModels),
            textEncoderOptions = setupOptions(state.textEncoderModels),
            llmOptions = setupOptions(state.llmModels),
            onFormChange = onImagePresetFormChange,
        )
        ImagePresetGenerationParameterFields(
            form = state.imageForm,
            samplerOptions = setupSamplerOptions,
            onFormChange = onImagePresetFormChange,
        )
        ImagePresetPerformanceFields(state.imageForm, onImagePresetFormChange)
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
            onClick = onContinue,
            enabled = state.canFinish,
        ) {
            ButtonLabel(
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                text = "Continue",
            )
        }
    }
}

@Composable
private fun LlmRoleStep(
    state: SetupUiState,
    role: SetupLlmRole,
    enabled: Boolean,
    form: LlmPresetForm,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onFormChange: (LlmPresetForm) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    val isLastStep = role == SetupLlmRole.Assistant
    Text(
        text = "${role.displayName} LLM",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = role.setupDescription,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupPresetBlock(
        icon = Icons.Default.SmartToy,
        title = role.displayName,
        subtitle = role.setupSubtitle,
    ) {
        DeskCheckboxRow(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            title = "Create and assign ${role.displayName} preset",
        )
        if (enabled) {
            LlmPresetCoreFields(
                form = form,
                llmOptions = setupOptions(state.llmModels),
                projectorOptions = setupOptions(state.mmprojModels),
                onFormChange = onFormChange,
            )
            LlmPresetAdvancedArgsField(form, onFormChange)
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeskOutlinedButton(onClick = onSkip, enabled = !state.isFinishing) {
                Text(if (isLastStep) "Skip and Finish" else "Skip")
            }
            DeskButton(
                onClick = onContinue,
                enabled = !state.isFinishing,
            ) {
                ButtonLabel(
                    icon = {
                        if (state.isFinishing) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    text = when {
                        state.isFinishing -> "Finishing"
                        isLastStep -> "Finish Setup"
                        else -> "Continue"
                    },
                )
            }
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

private fun setupOptions(models: List<SetupModelOption>): List<String> =
    models.map { it.id }.filter { it.isNotBlank() }.distinct().sorted()

private val SetupLlmRole.setupDescription: String
    get() = when (this) {
        SetupLlmRole.Tagging -> "Create a multimodal LLM preset for gallery image tagging, or skip it for now."
        SetupLlmRole.PromptEnhancement -> "Create a prompt enhancement preset, or keep enhancement unassigned."
        SetupLlmRole.Assistant -> "Create a chat assistant preset, or finish without assigning one."
    }

private val SetupLlmRole.setupSubtitle: String
    get() = when (this) {
        SetupLlmRole.Tagging -> "Optional. A vision projector is required for image tagging."
        SetupLlmRole.PromptEnhancement -> "Optional. Used when enhancing prompts before generation."
        SetupLlmRole.Assistant -> "Optional. Used by the local assistant panel."
    }

private val setupSamplerOptions = listOf(
    "euler_a",
    "euler",
    "heun",
    "dpm2",
    "dpm++2m",
    "dpm++2mv2",
    "lcm",
)

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
