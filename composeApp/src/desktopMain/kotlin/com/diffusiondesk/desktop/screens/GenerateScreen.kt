package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.viewmodel.GenerationUiState

@Composable
fun GenerateScreen(
    state: GenerationUiState,
    backendState: BackendUiState,
    samplerOptions: List<String>,
    onModelIdChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onCfgScaleChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onSamplerChange: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GenerationCard(
                title = "Generation",
                subtitle = "Minimal native smoke test for POST /v1/images/generations.",
            ) {
                Text(
                    text = if (backendState.status == BackendStatus.Ready) {
                        "Backend ready at ${backendState.baseUrl}"
                    } else {
                        "Backend is not ready. Start it from Settings first."
                    },
                    color = if (backendState.status == BackendStatus.Ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.modelId,
                    onValueChange = onModelIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model ID (optional)") },
                    supportingText = {
                        Text("Leave blank to use the last active SD model. Set this if you want deterministic desktop testing.")
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    label = { Text("Prompt") },
                )
                OutlinedTextField(
                    value = state.negativePrompt,
                    onValueChange = onNegativePromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    label = { Text("Negative Prompt") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.width,
                        onValueChange = onWidthChange,
                        modifier = Modifier.widthIn(min = 120.dp),
                        label = { Text("Width") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.height,
                        onValueChange = onHeightChange,
                        modifier = Modifier.widthIn(min = 120.dp),
                        label = { Text("Height") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.steps,
                        onValueChange = onStepsChange,
                        modifier = Modifier.widthIn(min = 120.dp),
                        label = { Text("Steps") },
                        singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.cfgScale,
                        onValueChange = onCfgScaleChange,
                        modifier = Modifier.widthIn(min = 120.dp),
                        label = { Text("CFG Scale") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.seed,
                        onValueChange = onSeedChange,
                        modifier = Modifier.widthIn(min = 120.dp),
                        label = { Text("Seed") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.sampler,
                        onValueChange = onSamplerChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Sampler") },
                        supportingText = {
                            Text("Known samplers: ${samplerOptions.joinToString()}")
                        },
                        singleLine = true,
                    )
                }
                Button(
                    onClick = onGenerate,
                    enabled = !state.isGenerating && backendState.status == BackendStatus.Ready,
                ) {
                    Text(if (state.isGenerating) "Generating..." else "Generate")
                }
                state.message.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        GenerationCard(
            modifier = Modifier.weight(1f),
            title = "Result",
            subtitle = "The server returns URLs; this preview confirms the native client can resolve and display them.",
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (state.image != null) {
                    Image(
                        bitmap = state.image,
                        contentDescription = "Generated image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = "No generated image yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.usedSeed.isNotBlank()) {
                Text("Used seed: ${state.usedSeed}", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.resultUrl.isNotBlank()) {
                Text("URL: ${state.resultUrl}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GenerationCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
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
