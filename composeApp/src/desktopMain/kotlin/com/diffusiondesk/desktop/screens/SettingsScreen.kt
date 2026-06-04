package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.diffusiondesk.desktop.viewmodel.SettingsUiState
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton as Button
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    backendState: BackendUiState,
    onRepoRootChange: (String) -> Unit,
    onListenPortChange: (String) -> Unit,
    onModelDirChange: (String) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onActionBarPositionChange: (String) -> Unit,
    onSaveImagesAutomaticallyChange: (Boolean) -> Unit,
    onUseCurrentRepo: () -> Unit,
    onSaveLocal: () -> Unit,
    onStartBackend: () -> Unit,
    onStopBackend: () -> Unit,
    onApplyToBackend: () -> Unit,
    onReloadFromBackend: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        val useTwoColumns = maxWidth >= 980.dp
        if (useTwoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GeneralSettingsSection(
                        state = state,
                        onThemeModeChange = onThemeModeChange,
                        onActionBarPositionChange = onActionBarPositionChange,
                        onSaveImagesAutomaticallyChange = onSaveImagesAutomaticallyChange,
                        onOutputDirChange = onOutputDirChange,
                        onModelDirChange = onModelDirChange,
                        onSaveLocal = onSaveLocal,
                        onApplyToBackend = onApplyToBackend,
                    )
                    WorkerSection(
                        state = state,
                        backendState = backendState,
                        onStartBackend = onStartBackend,
                        onStopBackend = onStopBackend,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DesktopSettingsSection(
                        state = state,
                        backendState = backendState,
                        onRepoRootChange = onRepoRootChange,
                        onListenPortChange = onListenPortChange,
                        onUseCurrentRepo = onUseCurrentRepo,
                        onSaveLocal = onSaveLocal,
                        onApplyToBackend = onApplyToBackend,
                        onReloadFromBackend = onReloadFromBackend,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GeneralSettingsSection(
                    state = state,
                    onThemeModeChange = onThemeModeChange,
                    onActionBarPositionChange = onActionBarPositionChange,
                    onSaveImagesAutomaticallyChange = onSaveImagesAutomaticallyChange,
                    onOutputDirChange = onOutputDirChange,
                    onModelDirChange = onModelDirChange,
                    onSaveLocal = onSaveLocal,
                    onApplyToBackend = onApplyToBackend,
                )
                WorkerSection(
                    state = state,
                    backendState = backendState,
                    onStartBackend = onStartBackend,
                    onStopBackend = onStopBackend,
                )
                DesktopSettingsSection(
                    state = state,
                    backendState = backendState,
                    onRepoRootChange = onRepoRootChange,
                    onListenPortChange = onListenPortChange,
                    onUseCurrentRepo = onUseCurrentRepo,
                    onSaveLocal = onSaveLocal,
                    onApplyToBackend = onApplyToBackend,
                    onReloadFromBackend = onReloadFromBackend,
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsSection(
    state: SettingsUiState,
    onThemeModeChange: (String) -> Unit,
    onActionBarPositionChange: (String) -> Unit,
    onSaveImagesAutomaticallyChange: (Boolean) -> Unit,
    onOutputDirChange: (String) -> Unit,
    onModelDirChange: (String) -> Unit,
    onSaveLocal: () -> Unit,
    onApplyToBackend: () -> Unit,
) {
    SectionCard(
        title = "General",
        subtitle = "Display options and the folders used by image generation.",
    ) {
        SettingsDropdownRow(
            label = "Theme",
            value = state.themeMode.toTitleLabel(),
            options = listOf("System", "Light", "Dark"),
            onValueChange = { onThemeModeChange(it.lowercase()) },
        )
        SettingsDropdownRow(
            label = "Action Bar Position",
            value = state.actionBarPosition.toTitleLabel(),
            options = listOf("Bottom", "Top"),
            onValueChange = { onActionBarPositionChange(it.lowercase()) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = state.saveImagesAutomatically,
                onCheckedChange = onSaveImagesAutomaticallyChange,
            )
            Text("Save Images Automatically")
        }
        PathSettingRow(
            label = "Output Directory",
            value = state.outputDir,
            onValueChange = onOutputDirChange,
            buttonText = "Save Path",
            helper = "Path on the server where images will be saved and loaded from.",
            onSave = {
                onSaveLocal()
                onApplyToBackend()
            },
        )
        PathSettingRow(
            label = "Model Directory",
            value = state.modelDir,
            onValueChange = onModelDirChange,
            buttonText = "Save & Scan",
            helper = "Root directory to scan for models.",
            onSave = {
                onSaveLocal()
                onApplyToBackend()
            },
        )
    }
}

@Composable
private fun WorkerSection(
    state: SettingsUiState,
    backendState: BackendUiState,
    onStartBackend: () -> Unit,
    onStopBackend: () -> Unit,
) {
    SectionCard(
        title = "Image Worker",
        subtitle = "The local stable-diffusion.cpp worker used for generation.",
    ) {
        StatusLine("Status", backendState.status.name)
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
        }
    }
}

@Composable
private fun DesktopSettingsSection(
    state: SettingsUiState,
    backendState: BackendUiState,
    onRepoRootChange: (String) -> Unit,
    onListenPortChange: (String) -> Unit,
    onUseCurrentRepo: () -> Unit,
    onSaveLocal: () -> Unit,
    onApplyToBackend: () -> Unit,
    onReloadFromBackend: () -> Unit,
) {
    SectionCard(
        title = "Advanced",
        subtitle = "Local worker launch settings and diagnostics.",
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        StatusLine("Base URL", backendState.baseUrl)
        StatusLine("Executable", backendState.executablePath.ifBlank { "Not resolved yet" })
        state.message.takeIf(String::isNotBlank)?.let { InfoText(it) }
        state.error?.let { ErrorText(it) }
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
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        DeskDropdownField(
            label = "",
            value = value,
            options = options,
            onValueChange = onValueChange,
            modifier = Modifier.widthIn(min = 140.dp, max = 180.dp),
        )
    }
}

@Composable
private fun PathSettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    buttonText: String,
    helper: String,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeskLabel(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            DeskTextField(
                label = "",
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave) {
                Text(buttonText)
            }
        }
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun String.toTitleLabel(): String = replaceFirstChar { char ->
    if (char.isLowerCase()) char.titlecase() else char.toString()
}
