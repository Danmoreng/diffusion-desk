package com.diffusiondesk.desktop.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppNotificationType {
    Info,
    Success,
    Warning,
    Error,
}

data class AppNotification(
    val id: String,
    val type: AppNotificationType,
    val title: String = "",
    val message: String,
    val createdAtMillis: Long,
    val autoDismissMillis: Long?,
    val count: Int = 1,
)

class NotificationCenter(
    private val scope: CoroutineScope,
) {
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
    private val dismissJobs = mutableMapOf<String, Job>()

    fun info(message: String, title: String = "") {
        add(AppNotificationType.Info, message, title)
    }

    fun success(message: String, title: String = "") {
        add(AppNotificationType.Success, message, title)
    }

    fun warning(message: String, title: String = "") {
        add(AppNotificationType.Warning, message, title)
    }

    fun error(message: String, title: String = "") {
        add(AppNotificationType.Error, message, title)
    }

    fun dismiss(id: String) {
        dismissJobs.remove(id)?.cancel()
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }

    fun clear() {
        dismissJobs.values.forEach(Job::cancel)
        dismissJobs.clear()
        _notifications.value = emptyList()
    }

    private fun add(type: AppNotificationType, message: String, title: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return

        val now = System.currentTimeMillis()
        val existing = _notifications.value.lastOrNull {
            it.type == type &&
                it.title == title &&
                it.message == trimmed &&
                now - it.createdAtMillis <= DEDUPE_WINDOW_MS
        }
        if (existing != null) {
            val updated = existing.copy(createdAtMillis = now, count = existing.count + 1)
            _notifications.value = _notifications.value.map { if (it.id == existing.id) updated else it }
            scheduleDismiss(updated)
            return
        }

        val notification = AppNotification(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            message = trimmed,
            createdAtMillis = now,
            autoDismissMillis = defaultDismissMs(type),
        )
        val next = (_notifications.value + notification).takeLast(MAX_VISIBLE)
        val visibleIds = next.map { it.id }.toSet()
        dismissJobs.keys.filterNot { it in visibleIds }.forEach { id -> dismissJobs.remove(id)?.cancel() }
        _notifications.value = next
        scheduleDismiss(notification)
    }

    private fun scheduleDismiss(notification: AppNotification) {
        val delayMs = notification.autoDismissMillis ?: return
        dismissJobs.remove(notification.id)?.cancel()
        dismissJobs[notification.id] = scope.launch {
            delay(delayMs)
            dismiss(notification.id)
        }
    }

    private fun defaultDismissMs(type: AppNotificationType): Long? {
        return when (type) {
            AppNotificationType.Info -> 5_000L
            AppNotificationType.Success -> 4_500L
            AppNotificationType.Warning -> 8_000L
            AppNotificationType.Error -> null
        }
    }

    private companion object {
        const val MAX_VISIBLE = 3
        const val DEDUPE_WINDOW_MS = 2_000L
    }
}
