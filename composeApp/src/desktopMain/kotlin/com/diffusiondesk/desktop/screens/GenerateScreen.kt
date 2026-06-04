package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.GenerationStatus
import com.diffusiondesk.desktop.viewmodel.GenerationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    state: GenerationUiState,
    backendState: BackendUiState,
    samplerOptions: List<String>,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenerationPanel(
                state = state,
                samplerOptions = samplerOptions,
                onPromptChange = onPromptChange,
                onNegativePromptChange = onNegativePromptChange,
                onWidthChange = onWidthChange,
                onHeightChange = onHeightChange,
                onStepsChange = onStepsChange,
                onCfgScaleChange = onCfgScaleChange,
                onSeedChange = onSeedChange,
                onSamplerChange = onSamplerChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )

            PreviewPanel(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        ActionBar(
            state = state,
            backendState = backendState,
            onGenerate = onGenerate,
            onToggleEndless = onToggleEndless,
            onGoBack = onGoBack,
            onGoForward = onGoForward,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerationPanel(
    state: GenerationUiState,
    samplerOptions: List<String>,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label("Prompt")
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 142.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            OutlinedTextField(
                value = state.negativePrompt,
                onValueChange = onNegativePromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 74.dp),
                label = { Text("Negative Prompt") },
            )

            Label("Parameters")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactNumberField("Steps", state.steps, onStepsChange, Modifier.weight(1f))
                CompactNumberField("Seed", state.seed, onSeedChange, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactNumberField("Width", state.width, onWidthChange, Modifier.weight(1f))
                CompactNumberField("Height", state.height, onHeightChange, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactNumberField("CFG", state.cfgScale, onCfgScaleChange, Modifier.weight(1f))
                SamplerMenu(
                    value = state.sampler,
                    options = samplerOptions,
                    onChange = onSamplerChange,
                    modifier = Modifier.weight(1f),
                )
            }

            state.currentHistoryItem?.let { item ->
                Label("Selected Generation")
                Text(
                    text = when (item.status) {
                        GenerationStatus.Pending -> "Queued"
                        GenerationStatus.Processing -> "Generating"
                        GenerationStatus.Completed -> "Completed"
                        GenerationStatus.Failed -> "Failed"
                    },
                    color = when (item.status) {
                        GenerationStatus.Completed -> MaterialTheme.colorScheme.primary
                        GenerationStatus.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                item.generationTime?.let {
                    Text(
                        text = "Generated in ${"%.1f".format(it)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.message.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    state: GenerationUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isGenerating && state.currentHistoryItem?.status == GenerationStatus.Processing -> {
                    ProgressCard(state)
                }
                state.image != null -> {
                    Image(
                        bitmap = state.image,
                        contentDescription = "Generated image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                state.currentHistoryItem?.status == GenerationStatus.Pending -> {
                    Text("Queued", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text("No generated image yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: GenerationUiState) {
    val progressFraction = if (state.progressSteps > 0) {
        (state.progressStep.toFloat() / state.progressSteps.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val eta = if (state.progressSteps > 0 && state.progressStep > 0 && state.progressTime > 0.0) {
        val averageStep = state.progressTime / state.progressStep
        ((state.progressSteps - state.progressStep) * averageStep).toInt()
    } else {
        0
    }

    Column(
        modifier = Modifier.widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.progressPhase.ifBlank { "Generating..." },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Generating image(s)...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Step ${state.progressStep} / ${state.progressSteps}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildString {
                    append("${"%.1f".format(state.progressTime)}s")
                    if (eta > 0) append("   Remaining: ~${eta}s")
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.progressMessage.isNotBlank()) {
            Text(
                text = state.progressMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionBar(
    state: GenerationUiState,
    backendState: BackendUiState,
    onGenerate: () -> Unit,
    onToggleEndless: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onGenerate,
                enabled = backendState.status == BackendStatus.Ready && state.prompt.isNotBlank(),
                modifier = Modifier
                    .height(52.dp)
                    .width(240.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        state.isGenerating && state.queueCount > 0 -> "Queue"
                        state.isGenerating -> "Generating..."
                        else -> "Generate"
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (state.queueCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("(${state.queueCount})")
                }
            }

            IconButton(
                onClick = onToggleEndless,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (state.isEndless) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Endless generation",
                    tint = if (state.isEndless) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(onClick = onGoBack, enabled = state.canGoBack) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous generation")
                }
                Surface(
                    modifier = Modifier.height(44.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Box(
                        modifier = Modifier.width(84.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.history.isEmpty()) "0 / 0" else "${state.historyIndex + 1} / ${state.history.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                IconButton(onClick = onGoForward, enabled = state.canGoForward) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next generation")
                }
            }

            Text(
                text = when (backendState.status) {
                    BackendStatus.Ready -> "Worker ready"
                    BackendStatus.Starting -> "Worker starting"
                    BackendStatus.Error -> "Worker error"
                    BackendStatus.Stopped -> "Worker stopped"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (backendState.status == BackendStatus.Ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamplerMenu(
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            label = { Text("Sampler") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
