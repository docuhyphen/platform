package com.docuhyphen.app.api.model.notification

import com.docuhyphen.app.api.model.dto.NotificationReadReceiptRequest
import com.docuhyphen.app.api.model.dto.NotificationReadReceiptResponse
import java.util.*

object NotificationReadReceiptDtoMapper
{
    fun toCriteria(request: NotificationReadReceiptRequest, notificationIds: Set<UUID>): NotificationReadCriteria
    {
        return NotificationReadCriteria(
            all = request.all,
            notificationIds = notificationIds,
            eventTypes = request.eventTypes.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
            data = request.data.mapKeys { it.key.trim() }
                .mapValues { it.value.trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() },
        )
    }

    fun toResponse(result: NotificationReadResult): NotificationReadReceiptResponse
    {
        return NotificationReadReceiptResponse(
            readNotificationIds = result.readNotificationIds.map { it.toString() },
            unreadCount = result.unreadCount,
        )
    }
}
