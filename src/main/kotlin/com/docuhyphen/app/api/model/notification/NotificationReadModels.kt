package com.docuhyphen.app.api.model.notification

import java.util.UUID

data class NotificationReadCriteria(
    val all: Boolean = false,
    val notificationIds: Set<UUID> = emptySet(),
    val eventTypes: Set<String> = emptySet(),
    val data: Map<String, String> = emptyMap(),
)

data class NotificationReadResult(
    val readNotificationIds: List<UUID>,
    val unreadCount: Long,
)
