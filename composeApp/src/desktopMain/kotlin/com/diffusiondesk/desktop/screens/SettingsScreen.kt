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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.SettingsUiState
import org.jetbrains.jewel.ui.component.Checkbox
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
    onShowLlmDebugConsoleChange: (Boolean) -> Unit,
    onVramBudgetModeChange: (String) -> Unit,
    onManualVramBudgetGbChange: (String) -> Unit,
    onUseCurrentRepo: () -> Unit,
    onSaveLocal: () -> Unit,
    onApplyToBackend: () -> Unit,
    onReloadFromBackend: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DeskScreenPadding),
    ) {
        val useTwoColumns = maxWidth >= 980.dp
        if (useTwoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
                ) {
                    GeneralSettingsSection(
                        state = state,
                        onThemeModeChange = onThemeModeChange,
                        onActionBarPositionChange = onActionBarPositionChange,
                        onSaveImagesAutomaticallyChange = onSaveImagesAutomaticallyChange,
                        onShowLlmDebugConsoleChange = onShowLlmDebugConsoleChange,
                        onVramBudgetModeChange = onVramBudgetModeChange,
                        onManualVramBudgetGbChange = onManualVramBudgetGbChange,
                        onOutputDirChange = onOutputDirChange,
                        onModelDirChange = onModelDirChange,
                        onSaveLocal = onSaveLocal,
                        onApplyToBackend = onApplyToBackend,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
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
                verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
            ) {
                GeneralSettingsSection(
                    state = state,
                    onThemeModeChange = onThemeModeChange,
                    onActionBarPositionChange = onActionBarPositionChange,
                    onSaveImagesAutomaticallyChange = onSaveImagesAutomaticallyChange,
                    onShowLlmDebugConsoleChange = onShowLlmDebugConsoleChange,
                    onVramBudgetModeChange = onVramBudgetModeChange,
                    onManualVramBudgetGbChange = onManualVramBudgetGbChange,
                    onOutputDirChange = onOutputDirChange,
                    onModelDirChange = onModelDirChange,
                    onSaveLocal = onSaveLocal,
                    onApplyToBackend = onApplyToBackend,
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
    onShowLlmDebugConsoleChange: (Boolean) -> Unit,
    onVramBudgetModeChange: (String) -> Unit,
    onManualVramBudgetGbChange: (String) -> Unit,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = state.showLlmDebugConsole,
                onCheckedChange = onShowLlmDebugConsoleChange,
            )
            Column {
                Text("Show LLM Debug Console")
                Text(
                    text = "Displays complete system prompts, user prompts, and model responses in Generate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SettingsDropdownRow(
            label = "Streaming VRAM Budget",
            value = if (state.vramBudgetMode == "manual") "Manual" else "Auto",
            options = listOf("Auto", "Manual"),
            onValueChange = { onVramBudgetModeChange(it.lowercase()) },
        )
        if (state.vramBudgetMode == "manual") {
            DeskTextField(
                label = "VRAM Budget (GiB)",
                value = state.manualVramBudgetGb,
                onValueChange = onManualVramBudgetGbChange,
                modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
            )
        } else {
            Text(
                text = "Auto reserves 2 GiB of currently free GPU memory for the desktop and other applications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap)) {
            DeskTextField(
                label = "Listen Port",
                value = state.listenPort,
                onValueChange = onListenPortChange,
                modifier = Modifier.widthIn(min = 160.dp),
            )
            DeskButton(onClick = onUseCurrentRepo) {
                Text("Use Detected Repo")
            }
            DeskButton(onClick = onSaveLocal) {
                Text("Save Local")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap)) {
            DeskButton(
                onClick = onApplyToBackend,
                enabled = !state.isBusy && backendState.status == BackendStatus.Ready,
            ) {
                Text("Apply Config")
            }
            DeskButton(
                onClick = onReloadFromBackend,
                enabled = !state.isBusy && backendState.status == BackendStatus.Ready,
            ) {
                Text("Reload Config")
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    DeskSection(
        title = title,
        subtitle = subtitle,
    ) {
        content()
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
            DeskButton(onClick = onSave) {
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

private fun String.toTitleLabel(): String = replaceFirstChar { char ->
    if (char.isLowerCase()) char.titlecase() else char.toString()
}
