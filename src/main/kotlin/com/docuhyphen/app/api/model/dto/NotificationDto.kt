package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class NotificationDto(
    val id: String,
    val type: NotificationType,
    val message: String,
    @Serializable(with = TimestampSerializer::class)
    val timestamp: Timestamp,
    val sessionId: String? = null,
    val documentId: String? = null,
    val commentId: String? = null,
    val userId: String? = null,
    val isRead: Boolean = false,
    val data: Map<String, String> = emptyMap()
)

enum class NotificationType
{
    NEW_COMMENT,
    NEW_SESSION,
    DOCUMENT_ADDED,
    DOCUMENT_UPDATED,
    SESSION_ENDED,
    SESSION_INITATED
}