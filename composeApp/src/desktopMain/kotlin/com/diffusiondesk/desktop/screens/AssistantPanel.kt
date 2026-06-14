package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.viewmodel.AssistantContextSnapshot
import com.diffusiondesk.desktop.viewmodel.AssistantDebugInfo
import com.diffusiondesk.desktop.viewmodel.AssistantMessage
import com.diffusiondesk.desktop.viewmodel.AssistantMessageRole
import com.diffusiondesk.desktop.viewmodel.AssistantToolCall
import com.diffusiondesk.desktop.viewmodel.AssistantUiState
import java.util.Locale
import java.time.format.DateTimeFormatter
import org.jetbrains.jewel.ui.component.Text

@Composable
fun AssistantPanel(
    state: AssistantUiState,
    context: AssistantContextSnapshot,
    onSend: (String, AssistantContextSnapshot) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DeskPanelCornerRadius)
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isLoading) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight()
            .padding(vertical = DeskScreenPadding)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        AssistantHeader(
            isLoading = state.isLoading,
            usageLabel = state.lastUsageLabel,
            onClear = onClear,
            onClose = onClose,
        )
        AssistantDebugBar(state.debugInfo)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages) { message ->
                AssistantMessageBubble(message)
            }
            if (state.isLoading) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        AssistantInputBar(
            value = draft,
            onValueChange = { draft = it },
            isLoading = state.isLoading,
            onCancel = onCancel,
            onSend = {
                val value = draft.trim()
                if (value.isNotBlank()) {
                    onSend(value, context)
                    draft = ""
                }
            },
        )
    }
}

@Composable
private fun AssistantDebugBar(debugInfo: AssistantDebugInfo?) {
    val info = debugInfo ?: return
    val contextLimit = info.contextLimitTokens?.let(::formatTokens) ?: "?"
    val promptUsage = info.promptTokens?.let(::formatTokens) ?: "est ${formatTokens(info.estimatedPromptTokens)}"
    val completionUsage = info.completionTokens?.let(::formatTokens) ?: "-"
    val totalUsage = info.totalTokens?.let(::formatTokens) ?: "-"
    val promptTps = info.promptTokensPerSecond?.let { "${formatDecimal(it)} p/s" } ?: "-"
    val predictedTps = info.predictedTokensPerSecond?.let { "${formatDecimal(it)} t/s" } ?: "-"
    val elapsed = info.elapsedMs?.let { "${it}ms" } ?: "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Context $promptUsage / $contextLimit (${info.contextLimitSource})  out $completionUsage  total $totalUsage",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Speed $predictedTps  prompt $promptTps  elapsed $elapsed  finish ${info.finishReason ?: "-"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(info.presetName ?: "No preset")
                info.workerBaseUrl?.let { append("  ").append(it) }
                info.maxTokens?.let { append("  max ").append(formatTokens(it)) }
                if (info.reasoningChars > 0) append("  reasoning chars ").append(info.reasoningChars)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssistantHeader(
    isLoading: Boolean,
    usageLabel: String?,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Assistant",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        usageLabel?.let {
            Text(
                text = it,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        DeskIconButton(
            icon = Icons.Default.DeleteOutline,
            contentDescription = "Clear assistant history",
            onClick = onClear,
            enabled = !isLoading,
            tooltip = "Clear history",
        )
        DeskIconButton(
            icon = Icons.Default.Close,
            contentDescription = "Close assistant",
            onClick = onClose,
            tooltip = "Close",
        )
    }
}

@Composable
private fun AssistantMessageBubble(message: AssistantMessage) {
    val isUser = message.role == AssistantMessageRole.User
    val isSystem = message.role == AssistantMessageRole.System
    val isTool = message.role == AssistantMessageRole.Tool
    if (isTool) {
        message.toolCall?.let { toolCall ->
            AssistantToolCallCard(toolCall, message)
        }
        return
    }
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isSystem -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isSystem -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val time = remember(message.timestamp) {
        message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(8.dp))
                .background(bubbleColor)
                .border(
                    1.dp,
                    if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isUser && !isSystem) {
                Text(
                    text = "Assistant",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            Text(
                text = time,
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun AssistantToolCallCard(toolCall: AssistantToolCall, message: AssistantMessage) {
    var expanded by remember(message.timestamp, toolCall.name) { mutableStateOf(false) }
    val time = remember(message.timestamp) {
        message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val shape = RoundedCornerShape(8.dp)
    val statusLabel = when {
        toolCall.success -> "Completed"
        toolCall.output == "Running..." -> "Pending"
        else -> "Failed"
    }
    val statusColor = when (statusLabel) {
        "Completed" -> MaterialTheme.colorScheme.primary
        "Failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Tool Call:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = toolCall.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse tool call" else "Expand tool call",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolPayloadBlock(label = "Input", value = toolCall.input)
                ToolPayloadBlock(label = "Output", value = toolCall.output)
                Text(
                    text = time,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun ToolPayloadBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                .padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeskIconButton(
            icon = Icons.Default.Image,
            contentDescription = "Attach image",
            onClick = {},
            enabled = false,
            tooltip = "Image attachments will be added with vision assistant support",
        )
        DeskIconButton(
            icon = Icons.Default.AutoFixHigh,
            contentDescription = "Attach current image",
            onClick = {},
            enabled = false,
            tooltip = "Attach current image will be added with vision assistant support",
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 96.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !isLoading,
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                            if (value.isNotBlank() && !isLoading) {
                                onSend()
                            }
                            true
                        } else {
                            false
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = "Ask me anything...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        Box(
            modifier = Modifier
                .height(40.dp)
                .width(48.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (value.isBlank() && !isLoading) 0.45f else 1f))
                .then(
                    if (isLoading) {
                        Modifier.clickable(onClick = onCancel)
                    } else if (value.isNotBlank()) {
                        Modifier.clickable(onClick = onSend)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatTokens(value: Int): String {
    return when {
        value >= 1024 -> "${formatDecimal(value / 1024.0)}k"
        else -> value.toString()
    }
}

private fun formatDecimal(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}
