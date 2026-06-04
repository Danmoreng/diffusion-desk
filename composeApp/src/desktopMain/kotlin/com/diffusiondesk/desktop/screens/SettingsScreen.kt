package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.GenerationUiState
import com.diffusiondesk.desktop.viewmodel.SettingsUiState
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton as Button
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    backendState: BackendUiState,
    generationState: GenerationUiState,
    onRepoRootChange: (String) -> Unit,
    onListenPortChange: (String) -> Unit,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onSetupCompletedChange: (Boolean) -> Unit,
    onUseCurrentRepo: () -> Unit,
    onSaveLocal: () -> Unit,
    onStartBackend: () -> Unit,
    onStopBackend: () -> Unit,
    onApplyToBackend: () -> Unit,
    onReloadFromBackend: () -> Unit,
    onPresetIdChange: (String) -> Unit,
    onReloadPresets: () -> Unit,
    onLoadPreset: () -> Unit,
) {
    val selectedPreset = generationState.presets.firstOrNull { it.id == generationState.selectedPresetId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = "Image Worker",
            subtitle = "The desktop app starts the stable-diffusion.cpp worker automatically and keeps the old orchestrator unused.",
        ) {
            StatusLine("Status", backendState.status.name)
            StatusLine("Base URL", backendState.baseUrl)
            StatusLine("Executable", backendState.executablePath.ifBlank { "Not resolved yet" })
            StatusLine("Message", backendState.message)
            if (backendState.lastLogLine.isNotBlank()) {
                StatusLine("Last log", backendState.lastLogLine)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStartBackend,
                    enabled = !state.isBusy && backendState.status != BackendStatus.Ready,
                ) {
                    Text("Start Worker")
                }
                Button(
                    onClick = onStopBackend,
                    enabled = !state.isBusy && backendState.status != BackendStatus.Stopped,
                ) {
                    Text("Stop Worker")
                }
                Button(
                    onClick = onApplyToBackend,
                    enabled = !state.isBusy && backendState.status == BackendStatus.Ready,
                ) {
                    Text("Apply Config")
                }
                Button(
                    onClick = onReloadFromBackend,
                    enabled = !state.isBusy && backendState.status == BackendStatus.Ready,
                ) {
                    Text("Reload Config")
                }
            }
        }

        SectionCard(
            title = "Image Model",
            subtitle = "Choose and load the image preset used by text-to-image generation.",
        ) {
            StatusLine("Preset Folder", generationState.presets.size.toString() + " preset(s)")
            StatusLine("Selected Preset", selectedPreset?.name ?: "None")
            StatusLine("Diffusion Model", selectedPreset?.diffusionModel ?: "Not selected")
            val textEncoder = selectedPreset?.llm.orEmpty()
            val vae = selectedPreset?.vae.orEmpty()
            if (textEncoder.isNotBlank()) {
                StatusLine("Text Encoder", textEncoder)
            }
            if (vae.isNotBlank()) {
                StatusLine("VAE", vae)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeskDropdownField(
                    label = "Preset",
                    value = selectedPreset?.name ?: "None",
                    options = generationState.presets.map { it.name },
                    onValueChange = { name ->
                        generationState.presets.firstOrNull { it.name == name }?.let { preset ->
                            onPresetIdChange(preset.id)
                        }
                    },
                    modifier = Modifier.widthIn(min = 240.dp, max = 360.dp),
                )
                Button(
                    onClick = onReloadPresets,
                    enabled = !generationState.isLoadingPresets,
                ) {
                    Text(if (generationState.isLoadingPresets) "Refreshing..." else "Refresh Presets")
                }
                Button(
                    onClick = onLoadPreset,
                    enabled = backendState.status == BackendStatus.Ready && !generationState.isLoadingPreset && selectedPreset != null,
                ) {
                    Text(if (generationState.isLoadingPreset) "Loading..." else "Load Preset")
                }
            }
        }

        SectionCard(
            title = "Desktop Settings",
            subtitle = "These settings are stored locally and passed to the image worker on startup.",
        ) {
            DeskTextField(
                label = "Repository Root",
                value = state.repoRoot,
                onValueChange = onRepoRootChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeskTextField(
                    label = "Listen Port",
                    value = state.listenPort,
                    onValueChange = onListenPortChange,
                    modifier = Modifier.widthIn(min = 160.dp),
                )
                Button(onClick = onUseCurrentRepo) {
                    Text("Use Detected Repo")
                }
                Button(onClick = onSaveLocal) {
                    Text("Save Local")
                }
            }
            DeskTextField(
                label = "Model Directory",
                value = state.modelDir,
                onValueChange = onModelDirChange,
                modifier = Modifier.fillMaxWidth(),
            )
            DeskTextField(
                label = "Output Directory",
                value = state.outputDir,
                onValueChange = onOutputDirChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = state.setupCompleted,
                    onCheckedChange = onSetupCompletedChange,
                )
                Text("Mark setup as completed")
            }
            state.message.takeIf(String::isNotBlank)?.let { InfoText(it) }
            state.error?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    DeskPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
