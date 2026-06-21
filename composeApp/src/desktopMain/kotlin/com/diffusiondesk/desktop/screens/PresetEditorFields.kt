package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.core.LlmPlacement
import com.diffusiondesk.desktop.core.ModelSummary
import com.diffusiondesk.desktop.viewmodel.ImagePresetForm
import com.diffusiondesk.desktop.viewmodel.LlmPresetForm
import org.jetbrains.jewel.ui.component.Text

internal fun modelOptionsFor(models: List<ModelSummary>, vararg types: String): List<String> {
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
internal fun LlmPresetCoreFields(
    form: LlmPresetForm,
    llmOptions: List<String>,
    projectorOptions: List<String>,
    onFormChange: (LlmPresetForm) -> Unit,
) {
    PresetLabeledField(
        label = "Preset Name",
        value = form.name,
        onValueChange = { onFormChange(form.copy(name = it)) },
        placeholder = "e.g. Small Tagger CPU",
        modifier = Modifier.fillMaxWidth(),
    )
    PresetModelPathField(
        label = "Model Path",
        value = form.modelPath,
        options = llmOptions,
        onValueChange = { onFormChange(form.copy(modelPath = it)) },
        placeholder = "llm/model.gguf or D:\\models\\model.gguf",
        modifier = Modifier.fillMaxWidth(),
    )
    PresetModelPathField(
        label = "Vision Projector (optional)",
        value = form.mmprojPath,
        options = projectorOptions,
        onValueChange = { onFormChange(form.copy(mmprojPath = it)) },
        placeholder = "llm/mmproj-model-f16.gguf",
        modifier = Modifier.fillMaxWidth(),
    )
    DeskDropdownField(
        label = "Placement",
        value = form.placement.name.uppercase(),
        options = listOf("AUTO", "CPU", "GPU"),
        onValueChange = {
            onFormChange(
                form.copy(
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

@Composable
internal fun LlmPresetAdvancedArgsField(
    form: LlmPresetForm,
    onFormChange: (LlmPresetForm) -> Unit,
) {
    PresetLabeledField(
        label = "Arguments",
        value = form.advancedArgs,
        onValueChange = { onFormChange(form.copy(advancedArgs = it)) },
        placeholder = "--ctx-size 4096 --threads 8",
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
    )
}

@Composable
internal fun ImagePresetModelComponentsFields(
    form: ImagePresetForm,
    mainModelOptions: List<String>,
    vaeOptions: List<String>,
    textEncoderOptions: List<String>,
    llmOptions: List<String>,
    onFormChange: (ImagePresetForm) -> Unit,
) {
    PresetModelPathField(
        label = "UNet / Main Model (required)",
        value = form.diffusionModel,
        options = mainModelOptions,
        onValueChange = { onFormChange(form.copy(diffusionModel = it)) },
        placeholder = "stable-diffusion/model.gguf or D:\\models\\model.gguf",
        modifier = Modifier.fillMaxWidth(),
    )
    PresetModelPathField(
        label = "Unconditional Diffusion Model (optional)",
        value = form.uncondDiffusionModel,
        options = mainModelOptions,
        onValueChange = { onFormChange(form.copy(uncondDiffusionModel = it)) },
        placeholder = "stable-diffusion/ideogram4_uncond-Q8_0.gguf",
        modifier = Modifier.fillMaxWidth(),
    )
    PresetTwoColumnRow {
        PresetModelPathField(
            label = "VAE (optional)",
            value = form.vae,
            options = vaeOptions,
            onValueChange = { onFormChange(form.copy(vae = it)) },
            placeholder = "vae/ae.safetensors",
            modifier = Modifier.weight(1f),
        )
        PresetModelPathField(
            label = "T5 / Text Encoder 2 (optional)",
            value = form.t5xxl,
            options = textEncoderOptions,
            onValueChange = { onFormChange(form.copy(t5xxl = it)) },
            placeholder = "text-encoder/t5.gguf",
            modifier = Modifier.weight(1f),
        )
    }
    PresetTwoColumnRow {
        PresetModelPathField(
            label = "LLM Text Encoder 3 (optional)",
            value = form.llm,
            options = llmOptions,
            onValueChange = { onFormChange(form.copy(llm = it)) },
            placeholder = "text-encoder/qwen.gguf",
            modifier = Modifier.weight(1f),
        )
        PresetModelPathField(
            label = "CLIP L (optional)",
            value = form.clipL,
            options = textEncoderOptions,
            onValueChange = { onFormChange(form.copy(clipL = it)) },
            placeholder = "clip/clip_l.gguf",
            modifier = Modifier.weight(1f),
        )
    }
    PresetModelPathField(
        label = "CLIP G (optional)",
        value = form.clipG,
        options = textEncoderOptions,
        onValueChange = { onFormChange(form.copy(clipG = it)) },
        placeholder = "clip/clip_g.gguf",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ImagePresetGenerationParameterFields(
    form: ImagePresetForm,
    samplerOptions: List<String>,
    onFormChange: (ImagePresetForm) -> Unit,
) {
    PresetPromptModeField(
        value = form.promptMode,
        onValueChange = { onFormChange(form.copy(promptMode = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    PresetTwoColumnRow {
        PresetLabeledField(
            label = "Width",
            value = form.defaultWidth,
            onValueChange = { onFormChange(form.copy(defaultWidth = it)) },
            placeholder = "1024",
            modifier = Modifier.weight(1f),
        )
        PresetLabeledField(
            label = "Height",
            value = form.defaultHeight,
            onValueChange = { onFormChange(form.copy(defaultHeight = it)) },
            placeholder = "1024",
            modifier = Modifier.weight(1f),
        )
    }
    PresetTwoColumnRow {
        PresetLabeledField(
            label = "Steps",
            value = form.defaultSteps,
            onValueChange = { onFormChange(form.copy(defaultSteps = it)) },
            placeholder = "4",
            modifier = Modifier.weight(1f),
        )
        PresetLabeledField(
            label = "CFG Scale",
            value = form.defaultCfgScale,
            onValueChange = { onFormChange(form.copy(defaultCfgScale = it)) },
            placeholder = "1.0",
            modifier = Modifier.weight(1f),
        )
    }
    PresetSamplerField(
        value = form.defaultSampler,
        options = samplerOptions,
        onValueChange = { onFormChange(form.copy(defaultSampler = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    PresetLabeledField(
        label = "Default Negative Prompt",
        value = form.defaultNegativePrompt,
        onValueChange = { onFormChange(form.copy(defaultNegativePrompt = it)) },
        placeholder = "deformed, blurry, low quality, watermark",
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
    )
}

@Composable
internal fun ImagePresetPerformanceFields(
    form: ImagePresetForm,
    onFormChange: (ImagePresetForm) -> Unit,
) {
    PresetToggleLine(
        checked = form.flashAttention,
        onCheckedChange = { onFormChange(form.copy(flashAttention = it)) },
        title = "Flash attention",
        subtitle = "Enable when supported by the selected model and backend build.",
    )
    PresetToggleLine(
        checked = form.streamLayers,
        onCheckedChange = { onFormChange(form.copy(streamLayers = it, offloadParamsToCpu = form.offloadParamsToCpu || it)) },
        title = "Stream diffusion layers",
        subtitle = "Stream diffusion layers through CPU memory. Parameter offload is enabled automatically.",
    )
    if (form.streamLayers) {
        DeskDropdownField(
            label = "VRAM Budget",
            value = if (form.useGlobalVramBudget) "Global setting" else "Preset override",
            options = listOf("Global setting", "Preset override"),
            onValueChange = { selected ->
                onFormChange(form.copy(useGlobalVramBudget = selected == "Global setting"))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!form.useGlobalVramBudget) {
            PresetLabeledField(
                label = "Preset VRAM Budget (GiB)",
                value = form.maxVramGb,
                onValueChange = { onFormChange(form.copy(maxVramGb = it)) },
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
    PresetToggleLine(
        checked = form.clipOnCpu,
        onCheckedChange = { onFormChange(form.copy(clipOnCpu = it)) },
        title = "CLIP on CPU",
        subtitle = "Reserve GPU memory when the text encoder does not fit alongside the diffusion model.",
    )
    PresetToggleLine(
        checked = form.vaeOnCpu,
        onCheckedChange = { onFormChange(form.copy(vaeOnCpu = it)) },
        title = "VAE on CPU",
        subtitle = "Fallback for high-resolution VAE decoding or VAE out-of-memory errors.",
    )
    if (!form.streamLayers) {
        PresetToggleLine(
            checked = form.offloadParamsToCpu,
            onCheckedChange = { onFormChange(form.copy(offloadParamsToCpu = it)) },
            title = "Offload parameters to CPU",
            subtitle = "Keep model parameters in system memory when layer streaming is disabled.",
        )
    }
}

@Composable
internal fun PresetTwoColumnRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
internal fun PresetLabeledField(
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
internal fun PresetModelPathField(
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
private fun PresetSamplerField(
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
private fun PresetPromptModeField(
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
private fun PresetToggleLine(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
) {
    DeskCheckboxRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        subtitle = subtitle,
    )
}

@Composable
internal fun StatusMessages(message: String, error: String?) {
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
