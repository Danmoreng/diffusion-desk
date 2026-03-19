package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.SettingsUiState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    backendState: BackendUiState,
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = "Backend",
            subtitle = "Launch the local Diffusion Desk server and push desktop settings into /v1/config.",
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
                    Text("Start Backend")
                }
                Button(
                    onClick = onStopBackend,
                    enabled = !state.isBusy && backendState.status != BackendStatus.Stopped,
                ) {
                    Text("Stop Backend")
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
            title = "Desktop Settings",
            subtitle = "This stays local to the desktop app and seeds backend startup/config updates.",
        ) {
            OutlinedTextField(
                value = state.repoRoot,
                onValueChange = onRepoRootChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Repository Root") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.listenPort,
                    onValueChange = onListenPortChange,
                    modifier = Modifier.widthIn(min = 160.dp),
                    label = { Text("Listen Port") },
                    singleLine = true,
                )
                Button(onClick = onUseCurrentRepo) {
                    Text("Use Detected Repo")
                }
                Button(onClick = onSaveLocal) {
                    Text("Save Local")
                }
            }
            OutlinedTextField(
                value = state.modelDir,
                onValueChange = onModelDirChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model Directory") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.outputDir,
                onValueChange = onOutputDirChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Output Directory") },
                singleLine = true,
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
