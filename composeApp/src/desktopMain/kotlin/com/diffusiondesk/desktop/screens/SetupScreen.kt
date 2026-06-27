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
import androidx.compose.material.icons.filled.Memory
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
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

@Composable
fun SetupScreen(
    state: SetupUiState,
    onStart: () -> Unit,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onContinueFromModelGuide: () -> Unit,
    onImagePresetFormChange: (ImagePresetForm) -> Unit,
    onEnableTaggingLlmPresetChange: (Boolean) -> Unit,
    onTaggingLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onContinueFromTaggingGuide: () -> Unit,
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
                        onStart = onStart,
                        onModelDirChange = onModelDirChange,
                        onOutputDirChange = onOutputDirChange,
                        onScan = onScan,
                        onBack = onBack,
                        onContinueFromModelGuide = onContinueFromModelGuide,
                        onImagePresetFormChange = onImagePresetFormChange,
                        onEnableTaggingLlmPresetChange = onEnableTaggingLlmPresetChange,
                        onTaggingLlmPresetFormChange = onTaggingLlmPresetFormChange,
                        onContinueFromTaggingGuide = onContinueFromTaggingGuide,
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
                        onStart = onStart,
                        onModelDirChange = onModelDirChange,
                        onOutputDirChange = onOutputDirChange,
                        onScan = onScan,
                        onBack = onBack,
                        onContinueFromModelGuide = onContinueFromModelGuide,
                        onImagePresetFormChange = onImagePresetFormChange,
                        onEnableTaggingLlmPresetChange = onEnableTaggingLlmPresetChange,
                        onTaggingLlmPresetFormChange = onTaggingLlmPresetFormChange,
                        onContinueFromTaggingGuide = onContinueFromTaggingGuide,
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
                "Set up a local GGUF-first image workflow without downloading files inside the app."
            } else {
                "Follow the Hugging Face checklist, place the files in your model folders, then scan again."
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
            text = "A guided setup for model folders, starter files, and the first usable preset.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DeskSectionSpacing))
        SetupStepIndicator(
            number = "1",
            title = "Welcome",
            subtitle = "Setup path",
            icon = Icons.Default.CheckCircle,
            selected = state.step == 1,
            complete = state.step > 1,
        )
        SetupStepIndicator(
            number = "2",
            title = "Folders",
            subtitle = "Models and outputs",
            icon = Icons.Default.FolderOpen,
            selected = state.step == 2,
            complete = state.step > 2,
        )
        SetupStepIndicator(
            number = "3",
            title = "Starter Model",
            subtitle = "Z-Image Turbo",
            icon = Icons.Default.Image,
            selected = state.step == 3,
            complete = state.step > 3,
        )
        SetupStepIndicator(
            number = "4",
            title = "Tagging",
            subtitle = "Optional vision LLM",
            icon = Icons.Default.SmartToy,
            selected = state.step == 4,
            complete = state.step > 4,
        )
        SetupStepIndicator(
            number = "5",
            title = "Preset",
            subtitle = "Generation defaults",
            icon = Icons.Default.Memory,
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
    onStart: () -> Unit,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onContinueFromModelGuide: () -> Unit,
    onImagePresetFormChange: (ImagePresetForm) -> Unit,
    onEnableTaggingLlmPresetChange: (Boolean) -> Unit,
    onTaggingLlmPresetFormChange: (LlmPresetForm) -> Unit,
    onContinueFromTaggingGuide: () -> Unit,
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
            1 -> WelcomeStep(onStart = onStart)
            2 -> FolderStep(
                state = state,
                onBack = onBack,
                onModelDirChange = onModelDirChange,
                onOutputDirChange = onOutputDirChange,
                onScan = onScan,
            )
            3 -> StarterModelGuideStep(
                state = state,
                onBack = onBack,
                onScan = onScan,
                onImagePresetFormChange = onImagePresetFormChange,
                onContinue = onContinueFromModelGuide,
            )
            4 -> TaggingGuideStep(
                state = state,
                onBack = onBack,
                onScan = onScan,
                onEnabledChange = onEnableTaggingLlmPresetChange,
                onFormChange = onTaggingLlmPresetFormChange,
                onSkip = { onSkipLlmStep(SetupLlmRole.Tagging) },
                onContinue = onContinueFromTaggingGuide,
            )
            else -> ImagePresetStep(
                state = state,
                onBack = onBack,
                onImagePresetFormChange = onImagePresetFormChange,
                onContinue = onFinish,
            )
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Text(
        text = "Welcome",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "This setup helps you prepare the local files Diffusion Desk needs. Downloads stay in your browser; the app only checks the folders and creates presets.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupPresetBlock(
        icon = Icons.Default.Image,
        title = "Starter image model",
        subtitle = "Z-Image Turbo GGUF with its required text encoder and VAE.",
    ) {
        SetupBullet("Open the recommended Hugging Face pages from the checklist.")
        SetupBullet("Download the files yourself and place them in the matching model folders.")
        SetupBullet("Scan the folders, review the detected files, then create the starter preset.")
    }
    SetupPresetBlock(
        icon = Icons.Default.SmartToy,
        title = "Optional image tagger",
        subtitle = "Qwen3-VL 4B can be assigned for gallery tagging and image descriptions.",
    ) {
        SetupBullet("This is optional and can be skipped during first setup.")
        SetupBullet("Vision LLM presets need both the GGUF model and a matching mmproj file.")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        DeskButton(onClick = onStart) {
            Text("Start Setup")
        }
    }
}

@Composable
private fun FolderStep(
    state: SetupUiState,
    onBack: () -> Unit,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onScan: () -> Unit,
) {
    Text(
        text = "Folders and System Check",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Choose where Diffusion Desk should look for manually downloaded model files.",
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
        text = "Basis Preset",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "This preset is what the Generate workspace will use first. You can refine every value later.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupPresetBlock(
        icon = Icons.Default.CheckCircle,
        title = "What presets control",
        subtitle = "Presets keep model wiring and generation defaults together.",
    ) {
        SetupBullet("Quality presets mainly affect resolution, steps, sampler, CFG scale, and VRAM behavior.")
        SetupBullet("Model components stay explicit: image model, text encoder, and VAE are separate files.")
        SetupBullet("The Generate screen remains the full workspace; this setup only prepares the defaults.")
    }
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
                text = "Finish Setup",
            )
        }
    }
}

@Composable
private fun StarterModelGuideStep(
    state: SetupUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onImagePresetFormChange: (ImagePresetForm) -> Unit,
    onContinue: () -> Unit,
) {
    Text(
        text = "Starter Model",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Z-Image Turbo needs three local files before the first image preset can be saved.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelDownloadCard(
        title = "Z-Image Turbo GGUF",
        role = "Image model / transformer",
        url = zImageTurboUrl,
        targetPath = File(state.modelDir, "diffusion_models"),
        found = state.imageForm.diffusionModel.isNotBlank(),
        foundLabel = state.imageForm.diffusionModel.ifBlank { "No Z-Image GGUF selected" },
    )
    ModelDownloadCard(
        title = "Qwen3-4B GGUF",
        role = "Required text encoder for Z-Image prompts",
        url = qwenTextEncoderUrl,
        targetPath = File(state.modelDir, "text-encoder"),
        found = state.imageForm.llm.isNotBlank(),
        foundLabel = state.imageForm.llm.ifBlank { "No text encoder selected" },
    )
    ModelDownloadCard(
        title = "FLUX VAE ae.safetensors",
        role = "Required VAE decoder for image output",
        url = fluxVaeUrl,
        targetPath = File(state.modelDir, "vae"),
        found = state.imageForm.vae.isNotBlank(),
        foundLabel = state.imageForm.vae.ifBlank { "No VAE selected" },
    )
    SetupPresetBlock(
        icon = Icons.Default.Image,
        title = "Detected files",
        subtitle = "Adjust these only if the scan picked the wrong file.",
    ) {
        ImagePresetModelComponentsFields(
            form = state.imageForm,
            mainModelOptions = setupOptions(state.imageModels),
            vaeOptions = setupOptions(state.vaeModels),
            textEncoderOptions = setupOptions(state.textEncoderModels),
            llmOptions = setupOptions(state.llmModels),
            onFormChange = onImagePresetFormChange,
        )
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
            DeskOutlinedButton(onClick = onScan, enabled = state.canContinueFromFolders) {
                ButtonLabel(
                    icon = {
                        if (state.isScanning) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    text = if (state.isScanning) "Scanning" else "Scan Again",
                )
            }
            DeskButton(onClick = onContinue, enabled = state.canFinish) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun TaggingGuideStep(
    state: SetupUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onFormChange: (LlmPresetForm) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    Text(
        text = "Optional Image Tagger",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Qwen3-VL 4B can describe images and create gallery tags. You can skip this and add it later.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelDownloadCard(
        title = "Qwen3-VL 4B Instruct GGUF",
        role = "Vision-language model for image tagging",
        url = qwenVlUrl,
        targetPath = File(state.modelDir, "llm"),
        found = state.taggingLlmForm.modelPath.isNotBlank(),
        foundLabel = state.taggingLlmForm.modelPath.ifBlank { "No Qwen3-VL GGUF selected" },
    )
    ModelDownloadCard(
        title = "Qwen3-VL mmproj",
        role = "Required vision projector for image inputs",
        url = qwenVlUrl,
        targetPath = File(state.modelDir, "mmproj"),
        found = state.taggingLlmForm.mmprojPath.isNotBlank(),
        foundLabel = state.taggingLlmForm.mmprojPath.ifBlank { "No mmproj selected" },
    )
    SetupPresetBlock(
        icon = Icons.Default.SmartToy,
        title = "Image Tagging Preset",
        subtitle = "Enable this only if the model and projector are already in place.",
    ) {
        DeskCheckboxRow(
            checked = state.enableTaggingLlmPreset,
            onCheckedChange = onEnabledChange,
            title = "Create and assign Qwen3-VL image tagging preset",
        )
        if (state.enableTaggingLlmPreset) {
            LlmPresetCoreFields(
                form = state.taggingLlmForm,
                llmOptions = setupOptions(state.llmModels),
                projectorOptions = setupOptions(state.mmprojModels),
                onFormChange = onFormChange,
            )
            LlmPresetAdvancedArgsField(state.taggingLlmForm, onFormChange)
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
            DeskOutlinedButton(onClick = onScan, enabled = state.canContinueFromFolders) {
                ButtonLabel(
                    icon = {
                        if (state.isScanning) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    text = if (state.isScanning) "Scanning" else "Scan Again",
                )
            }
            DeskOutlinedButton(onClick = onSkip) {
                Text("Skip")
            }
            DeskButton(onClick = onContinue) {
                Text("Continue")
            }
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

@Composable
private fun ModelDownloadCard(
    title: String,
    role: String,
    url: String,
    targetPath: File,
    found: Boolean,
    foundLabel: String,
) {
    SetupPresetBlock(
        icon = if (found) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
        title = title,
        subtitle = role,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DeskStatusBadge(
                    text = if (found) "Found" else "Missing",
                    tone = if (found) DeskStatusTone.Success else DeskStatusTone.Warning,
                )
                Text(
                    text = foundLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = targetPath.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeskOutlinedButton(onClick = { openFolder(targetPath) }) {
                    Text("Folder")
                }
                DeskButton(onClick = { openUrl(url) }) {
                    Text("Hugging Face")
                }
            }
        }
    }
}

@Composable
private fun SetupBullet(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        Text("-", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private const val zImageTurboUrl = "https://huggingface.co/unsloth/Z-Image-Turbo-GGUF/tree/main"
private const val qwenTextEncoderUrl = "https://huggingface.co/unsloth/Qwen3-4B-GGUF/tree/main"
private const val fluxVaeUrl = "https://huggingface.co/black-forest-labs/FLUX.1-schnell/tree/main"
private const val qwenVlUrl = "https://huggingface.co/unsloth/Qwen3-VL-4B-Instruct-GGUF/tree/main"

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

private fun openUrl(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}

private fun openFolder(folder: File) {
    runCatching {
        folder.mkdirs()
        Desktop.getDesktop().open(folder)
    }
}
