package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.LlmWorkerState
import com.diffusiondesk.desktop.core.LlmWorkerStatus
import com.diffusiondesk.desktop.viewmodel.SettingsUiState
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text

private enum class SystemTab(val label: String) {
    Overview("Overview"),
    Diagnostics("Diagnostics"),
}

private data class LlmRoleRow(
    val label: String,
    val roleKey: String,
    val presetId: String,
    val onPresetChange: (String) -> Unit,
)

@Composable
fun SystemScreen(
    state: SettingsUiState,
    backendState: BackendUiState,
    onStartBackend: () -> Unit,
    onStopBackend: () -> Unit,
    onUnloadImageModel: () -> Unit,
    onReloadLlmPresets: () -> Unit,
    onTaggingPresetChange: (String) -> Unit,
    onAssistantPresetChange: (String) -> Unit,
    onPromptEnhancerPresetChange: (String) -> Unit,
    onAutostartLlmWorkersChange: (Boolean) -> Unit,
    onLoadLlmRole: (String) -> Unit,
    onUnloadLlmPreset: (String) -> Unit,
    onStopLlmWorker: (String) -> Unit,
    onStopAllLlmWorkers: () -> Unit,
    onTagNextImage: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(SystemTab.Overview) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DeskScreenPadding),
        verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing),
    ) {
        SystemTabHeader(
            activeTab = activeTab,
            onSelect = { activeTab = it },
        )

        when (activeTab) {
            SystemTab.Overview -> SystemOverview(
                state = state,
                backendState = backendState,
                onStartBackend = onStartBackend,
                onStopBackend = onStopBackend,
                onUnloadImageModel = onUnloadImageModel,
                onReloadLlmPresets = onReloadLlmPresets,
                onTaggingPresetChange = onTaggingPresetChange,
                onAssistantPresetChange = onAssistantPresetChange,
                onPromptEnhancerPresetChange = onPromptEnhancerPresetChange,
                onAutostartLlmWorkersChange = onAutostartLlmWorkersChange,
                onLoadLlmRole = onLoadLlmRole,
                onUnloadLlmPreset = onUnloadLlmPreset,
                onStopLlmWorker = onStopLlmWorker,
                onStopAllLlmWorkers = onStopAllLlmWorkers,
                onTagNextImage = onTagNextImage,
            )
            SystemTab.Diagnostics -> SystemDiagnostics(
                state = state,
                backendState = backendState,
                onUnloadLlmPreset = onUnloadLlmPreset,
                onStopLlmWorker = onStopLlmWorker,
            )
        }
    }
}

@Composable
private fun SystemOverview(
    state: SettingsUiState,
    backendState: BackendUiState,
    onStartBackend: () -> Unit,
    onStopBackend: () -> Unit,
    onUnloadImageModel: () -> Unit,
    onReloadLlmPresets: () -> Unit,
    onTaggingPresetChange: (String) -> Unit,
    onAssistantPresetChange: (String) -> Unit,
    onPromptEnhancerPresetChange: (String) -> Unit,
    onAutostartLlmWorkersChange: (Boolean) -> Unit,
    onLoadLlmRole: (String) -> Unit,
    onUnloadLlmPreset: (String) -> Unit,
    onStopLlmWorker: (String) -> Unit,
    onStopAllLlmWorkers: () -> Unit,
    onTagNextImage: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 980.dp
        if (twoColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(DeskSectionSpacing), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing)) {
                    SystemSummary(state, backendState)
                    ImageWorkerOverview(state, backendState, onStartBackend, onStopBackend, onUnloadImageModel)
                }
                Column(modifier = Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing)) {
                    LlmWorkersOverview(
                        state = state,
                        onReloadLlmPresets = onReloadLlmPresets,
                        onTaggingPresetChange = onTaggingPresetChange,
                        onAssistantPresetChange = onAssistantPresetChange,
                        onPromptEnhancerPresetChange = onPromptEnhancerPresetChange,
                        onAutostartLlmWorkersChange = onAutostartLlmWorkersChange,
                        onLoadLlmRole = onLoadLlmRole,
                        onUnloadLlmPreset = onUnloadLlmPreset,
                        onStopLlmWorker = onStopLlmWorker,
                        onStopAllLlmWorkers = onStopAllLlmWorkers,
                        onTagNextImage = onTagNextImage,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing), modifier = Modifier.fillMaxWidth()) {
                SystemSummary(state, backendState)
                ImageWorkerOverview(state, backendState, onStartBackend, onStopBackend, onUnloadImageModel)
                LlmWorkersOverview(
                    state = state,
                    onReloadLlmPresets = onReloadLlmPresets,
                    onTaggingPresetChange = onTaggingPresetChange,
                    onAssistantPresetChange = onAssistantPresetChange,
                    onPromptEnhancerPresetChange = onPromptEnhancerPresetChange,
                    onAutostartLlmWorkersChange = onAutostartLlmWorkersChange,
                    onLoadLlmRole = onLoadLlmRole,
                    onUnloadLlmPreset = onUnloadLlmPreset,
                    onStopLlmWorker = onStopLlmWorker,
                    onStopAllLlmWorkers = onStopAllLlmWorkers,
                    onTagNextImage = onTagNextImage,
                )
            }
        }
    }
}

@Composable
private fun SystemSummary(
    state: SettingsUiState,
    backendState: BackendUiState,
) {
    SystemSectionCard(title = "Status") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
        ) {
            SummaryTile(
                label = "Image Worker",
                value = backendState.status.name,
                status = backendState.status,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                label = "LLM Workers",
                value = activeLlmWorkerCount(state).toString(),
                status = if (activeLlmWorkerCount(state) > 0) LlmWorkerStatus.Ready else LlmWorkerStatus.Stopped,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ImageWorkerOverview(
    state: SettingsUiState,
    backendState: BackendUiState,
    onStartBackend: () -> Unit,
    onStopBackend: () -> Unit,
    onUnloadImageModel: () -> Unit,
) {
    SystemSectionCard(title = "Image Worker") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Local image engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = backendState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(backendState.status)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap)) {
            DeskButton(
                onClick = onStartBackend,
                enabled = !state.isBusy && backendState.status != BackendStatus.Ready,
            ) {
                Text("Start")
            }
            DeskButton(
                onClick = onStopBackend,
                enabled = !state.isBusy && backendState.status != BackendStatus.Stopped,
            ) {
                Text("Stop")
            }
            DeskButton(
                onClick = onUnloadImageModel,
                enabled = !state.isBusy && backendState.status == BackendStatus.Ready,
            ) {
                Text("Unload Model")
            }
        }
    }
}

@Composable
private fun LlmWorkersOverview(
    state: SettingsUiState,
    onReloadLlmPresets: () -> Unit,
    onTaggingPresetChange: (String) -> Unit,
    onAssistantPresetChange: (String) -> Unit,
    onPromptEnhancerPresetChange: (String) -> Unit,
    onAutostartLlmWorkersChange: (Boolean) -> Unit,
    onLoadLlmRole: (String) -> Unit,
    onUnloadLlmPreset: (String) -> Unit,
    onStopLlmWorker: (String) -> Unit,
    onStopAllLlmWorkers: () -> Unit,
    onTagNextImage: () -> Unit,
) {
    val roles = listOf(
        LlmRoleRow("Tagging", "tagging", state.llmRoles.taggingPresetId, onTaggingPresetChange),
        LlmRoleRow("Assistant", "assistant", state.llmRoles.assistantPresetId, onAssistantPresetChange),
        LlmRoleRow("Prompt Enhancer", "prompt enhancement", state.llmRoles.promptEnhancerPresetId, onPromptEnhancerPresetChange),
    )

    SystemSectionCard(title = "LLM Workers") {
        Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap)) {
            DeskButton(onClick = onReloadLlmPresets) {
                Text("Reload Presets")
            }
            DeskButton(
                onClick = onTagNextImage,
                enabled = !state.isBusy && state.llmRoles.taggingPresetId.isNotBlank(),
            ) {
                Text("Tag Next Image")
            }
            DeskButton(
                onClick = onStopAllLlmWorkers,
                enabled = state.llmWorkers.any { it.status != LlmWorkerStatus.Stopped && it.status != LlmWorkerStatus.Error },
            ) {
                Text("Stop All")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = state.autostartLlmWorkers,
                onCheckedChange = onAutostartLlmWorkersChange,
            )
            Text("Autostart configured LLM roles")
        }

        roles.forEach { role ->
            LlmRoleOverviewRow(
                state = state,
                role = role,
                onLoadLlmRole = onLoadLlmRole,
                onUnloadLlmPreset = onUnloadLlmPreset,
                onStopLlmWorker = onStopLlmWorker,
            )
        }
    }
}

@Composable
private fun LlmRoleOverviewRow(
    state: SettingsUiState,
    role: LlmRoleRow,
    onLoadLlmRole: (String) -> Unit,
    onUnloadLlmPreset: (String) -> Unit,
    onStopLlmWorker: (String) -> Unit,
) {
    val presetOptions = listOf("None") + state.llmPresets.map { it.name }
    fun selectedName(id: String): String = state.llmPresets.firstOrNull { it.id == id }?.name ?: "None"
    fun idForName(name: String): String = state.llmPresets.firstOrNull { it.name == name }?.id ?: ""
    val worker = state.llmWorkers.firstOrNull { it.presetId == role.presetId && role.presetId.isNotBlank() }

    DeskPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(role.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusBadge(worker?.status ?: LlmWorkerStatus.Stopped)
            }
            DeskDropdownField(
                label = "",
                value = selectedName(role.presetId),
                options = presetOptions,
                onValueChange = { role.onPresetChange(idForName(it)) },
                modifier = Modifier.weight(1f),
            )
            DeskButton(
                onClick = { onLoadLlmRole(role.roleKey) },
                enabled = !state.isBusy && role.presetId.isNotBlank() && worker?.status !in setOf(LlmWorkerStatus.Starting, LlmWorkerStatus.Loading, LlmWorkerStatus.Busy),
            ) {
                Text("Load")
            }
            DeskButton(
                onClick = { onUnloadLlmPreset(role.presetId) },
                enabled = worker?.status == LlmWorkerStatus.Ready,
            ) {
                Text("Unload")
            }
            if (worker != null) {
                DeskButton(
                    onClick = { onStopLlmWorker(worker.id) },
                    enabled = worker.status != LlmWorkerStatus.Stopped && worker.status != LlmWorkerStatus.Error,
                ) {
                    Text("Stop")
                }
            }
        }
        worker?.message?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SystemDiagnostics(
    state: SettingsUiState,
    backendState: BackendUiState,
    onUnloadLlmPreset: (String) -> Unit,
    onStopLlmWorker: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DeskSectionSpacing), modifier = Modifier.fillMaxWidth()) {
        SystemSectionCard(title = "Image Worker Diagnostics") {
            StatusLine("Status", backendState.status.name)
            StatusLine("Message", backendState.message)
            StatusLine("Base URL", backendState.baseUrl)
            StatusLine("Executable", backendState.executablePath.ifBlank { "Not resolved yet" })
            StatusLine("Last log", backendState.lastLogLine.ifBlank { "No log output yet." })
        }

        SystemSectionCard(title = "LLM Worker Diagnostics") {
            if (state.llmWorkers.isEmpty()) {
                Text("No LLM workers are active.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.llmWorkers.forEach { worker ->
                    LlmWorkerDiagnosticsCard(worker, onUnloadLlmPreset, onStopLlmWorker)
                }
            }
        }
    }
}

@Composable
private fun LlmWorkerDiagnosticsCard(
    worker: LlmWorkerState,
    onUnloadLlmPreset: (String) -> Unit,
    onStopLlmWorker: (String) -> Unit,
) {
    DeskPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(worker.presetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StatusBadge(worker.status)
        }
        StatusLine("Worker ID", worker.id)
        StatusLine("Base URL", worker.baseUrl)
        StatusLine("Model", worker.modelPath.ifBlank { "No model loaded." })
        StatusLine("Executable", worker.executablePath.ifBlank { "Not resolved yet." })
        StatusLine("Placement", worker.placement.name.uppercase())
        StatusLine("GPU layers", worker.nGpuLayers.toString())
        StatusLine("VRAM", "${worker.vramAllocatedMb} MB allocated, ${worker.vramFreeMb} MB free")
        StatusLine("Message", worker.message.ifBlank { "No status message." })
        StatusLine("Last log", worker.lastLogLine.ifBlank { "No log output yet." })
        if (worker.parsedArgs.isNotEmpty()) {
            StatusLine("Parsed args", worker.parsedArgs.joinToString(" "))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap)) {
            DeskButton(
                onClick = { onUnloadLlmPreset(worker.presetId) },
                enabled = worker.status == LlmWorkerStatus.Ready,
            ) {
                Text("Unload Model")
            }
            DeskButton(
                onClick = { onStopLlmWorker(worker.id) },
                enabled = worker.status != LlmWorkerStatus.Stopped && worker.status != LlmWorkerStatus.Error,
            ) {
                Text("Stop Worker")
            }
        }
    }
}

@Composable
private fun SystemTabHeader(
    activeTab: SystemTab,
    onSelect: (SystemTab) -> Unit,
) {
    DeskTabHeader(
        tabs = listOf(
            DeskTabItem(
                selected = activeTab == SystemTab.Overview,
                icon = Icons.Default.Dashboard,
                label = SystemTab.Overview.label,
                onClick = { onSelect(SystemTab.Overview) },
            ),
            DeskTabItem(
                selected = activeTab == SystemTab.Diagnostics,
                icon = Icons.Default.Terminal,
                label = SystemTab.Diagnostics.label,
                onClick = { onSelect(SystemTab.Diagnostics) },
            ),
        ),
    )
}

@Composable
private fun SystemSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    DeskPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    status: Any,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(status)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(DeskLayoutGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun StatusBadge(status: Any) {
    val color = statusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
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

private fun activeLlmWorkerCount(state: SettingsUiState): Int {
    return state.llmWorkers.count { it.status != LlmWorkerStatus.Stopped && it.status != LlmWorkerStatus.Error }
}

@Composable
private fun statusColor(status: Any): Color {
    return when (status) {
        BackendStatus.Ready, LlmWorkerStatus.Ready, LlmWorkerStatus.ReadyNoModel -> MaterialTheme.colorScheme.primary
        BackendStatus.Starting, LlmWorkerStatus.Starting, LlmWorkerStatus.Loading, LlmWorkerStatus.Busy -> Color(0xFFFFA000)
        BackendStatus.Error, LlmWorkerStatus.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
