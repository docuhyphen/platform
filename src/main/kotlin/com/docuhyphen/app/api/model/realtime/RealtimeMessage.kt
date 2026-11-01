package com.docuhyphen.app.api.model.realtime

import com.docuhyphen.app.api.model.dto.NotificationDto
import kotlinx.serialization.Serializable

@Serializable
data class UserSessionInfo(
    val sessionId: String,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdDate: String,
    val lastSeenAt: String,
    val expiresAt: String? = null,
    val isCurrent: Boolean = false,
)

@Serializable
data class RealtimeMessage(
    val type: String,
    val exchangeId: String? = null,
    val notification: NotificationDto? = null,
    val userId: String? = null,
    val userSessionId: String? = null,
    val online: Boolean? = null,
    val viewerUserIds: List<String>? = null,
    val reason: String? = null,
    val code: String? = null,
    val message: String? = null,
    val serverTime: Long? = null,
    val session: UserSessionInfo? = null,
    val documentId: String? = null,
    val commentId: String? = null,
    val status: String? = null,
)

object RealtimeMessageType
{
    const val PING = "PING"
    const val SUBSCRIBE_EXCHANGE = "SUBSCRIBE_EXCHANGE"
    const val UNSUBSCRIBE_EXCHANGE = "UNSUBSCRIBE_EXCHANGE"

    const val PONG = "PONG"
    const val SESSION_REVOKED = "SESSION_REVOKED"
    const val SESSION_CREATED = "SESSION_CREATED"
    const val SESSION_REMOVED = "SESSION_REMOVED"
    const val PASSWORD_CHANGED = "PASSWORD_CHANGED"
    const val SIGNED_OUT_OTHER_DEVICE = "SIGNED_OUT_OTHER_DEVICE"
    const val NOTIFICATION = "NOTIFICATION"
    const val PRESENCE_UPDATE = "PRESENCE_UPDATE"
    const val SHARING_VIEWERS = "SHARING_VIEWERS"
    const val EXCHANGE_DOCUMENT_ADDED = "EXCHANGE_DOCUMENT_ADDED"
    const val EXCHANGE_DOCUMENT_REMOVED = "EXCHANGE_DOCUMENT_REMOVED"
    const val EXCHANGE_DOCUMENT_UPDATED = "EXCHANGE_DOCUMENT_UPDATED"
    const val DOCUMENT_COMMENT_ADDED = "DOCUMENT_COMMENT_ADDED"
    const val EXCHANGE_STATUS_CHANGED = "EXCHANGE_STATUS_CHANGED"
    const val EXCHANGE_LIST_CHANGED = "EXCHANGE_LIST_CHANGED"
    const val REALTIME_PROBE = "REALTIME_PROBE"
    const val ERROR = "ERROR"
    const val WELCOME = "WELCOME"
}
