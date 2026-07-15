package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.entity.InAppNotification
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@ApplicationScoped
class InAppNotificationMapper
{
    private val json = Json { ignoreUnknownKeys = true }

    fun toDto(notification: InAppNotification): NotificationDto
    {
        val data = notification.payloadJson
            ?.let { payload ->
                runCatching {
                    json.decodeFromString(
                        MapSerializer(String.serializer(), String.serializer()),
                        payload,
                    )
                }.getOrDefault(emptyMap())
            }
            ?: emptyMap()

        return NotificationDto(
            id = notification.id.toString(),
            type = notification.eventType,
            message = notification.body?.takeIf { it.isNotBlank() } ?: notification.title,
            timestamp = notification.createdAt,
            exchangeId = data["exchangeId"] ?: data["exchange_id"]
                ?: data["subjectId"]?.takeIf { data["subjectType"] == "EXCHANGE" },
            documentId = data["documentId"] ?: data["document_id"],
            commentId = data["commentId"] ?: data["comment_id"],
            userId = notification.appUserId.toString(),
            isRead = notification.isRead,
            data = data,
        )
    }
}
