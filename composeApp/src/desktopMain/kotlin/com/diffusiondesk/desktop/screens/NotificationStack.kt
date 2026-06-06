package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.AppNotification
import com.diffusiondesk.desktop.core.AppNotificationType
import org.jetbrains.jewel.ui.component.Text

@Composable
fun NotificationStack(
    notifications: List<AppNotification>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notifications.isEmpty()) return

    Column(
        modifier = modifier.widthIn(min = 280.dp, max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        notifications.forEach { notification ->
            NotificationCard(
                notification = notification,
                onDismiss = { onDismiss(notification.id) },
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onDismiss: () -> Unit,
) {
    val color = notificationColor(notification.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = notificationIcon(notification.type),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (notification.count > 1) "${notification.message} (${notification.count})" else notification.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss notification",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(17.dp)
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun notificationColor(type: AppNotificationType): Color {
    return when (type) {
        AppNotificationType.Info -> MaterialTheme.colorScheme.primary
        AppNotificationType.Success -> Color(0xFF2E7D32)
        AppNotificationType.Warning -> Color(0xFFFFA000)
        AppNotificationType.Error -> MaterialTheme.colorScheme.error
    }
}

private fun notificationIcon(type: AppNotificationType): ImageVector {
    return when (type) {
        AppNotificationType.Info -> Icons.Default.Info
        AppNotificationType.Success -> Icons.Default.CheckCircle
        AppNotificationType.Warning -> Icons.Default.Warning
        AppNotificationType.Error -> Icons.Default.Error
    }
}
