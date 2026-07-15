package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.model.dto.NotificationDto
import kotlinx.serialization.Serializable

/**
 * Minimal session snapshot carried inside SESSION_CREATED / SESSION_REMOVED envelopes so
 * receiving clients can update their sessions-tab list without a round-trip.
 */
@Serializable
data class UserSessionInfo(
    val sessionId: String,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdDate: String,
    val lastSeenAt: String,
    val expiresAt: String? = null,
    /** Always false when broadcast to other devices,  the new session is not their session. */
    val isCurrent: Boolean = false,
)

/**
 * Wire protocol envelope for the /realtime/{userSessionId} WebSocket.
 *
 * Direction is implicit from `type`:
 *
 *   Client to Server
 *     - PING
 *     - SUBSCRIBE_EXCHANGE      { exchangeId }
 *     - UNSUBSCRIBE_EXCHANGE    { exchangeId }
 *
 *   Server to Client
 *     - PONG                            { serverTime }
 *     - SESSION_REVOKED                  { reason }
 *     - SESSION_CREATED                  { session }
 *     - SESSION_REMOVED                  { userSessionId }
 *     - PASSWORD_CHANGED
 *     - SIGNED_OUT_OTHER_DEVICE
 *     - NOTIFICATION                    { notification }
 *     - PRESENCE_UPDATE                 { userId, online }
 *     - SHARING_VIEWERS                 { exchangeId, viewerUserIds }
 *     - EXCHANGE_DOCUMENT_ADDED   { exchangeId, documentId }
 *     - EXCHANGE_DOCUMENT_REMOVED { exchangeId, documentId }
 *     - EXCHANGE_DOCUMENT_UPDATED { exchangeId, documentId }
 *     - EXCHANGE_STATUS_CHANGED   { exchangeId, status }
 *     - EXCHANGE_LIST_CHANGED     { exchangeId, status }
 *     - REALTIME_PROBE                   { message, serverTime }
 *     - ERROR                           { code, message }
 *     - WELCOME                         { userSessionId, serverTime }
 */
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
    val status: String? = null,
)

object RealtimeMessageType
{
    // Client to Server
    const val PING = "PING"
    const val SUBSCRIBE_EXCHANGE = "SUBSCRIBE_EXCHANGE"
    const val UNSUBSCRIBE_EXCHANGE = "UNSUBSCRIBE_EXCHANGE"

    // Server to Client
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
    const val EXCHANGE_STATUS_CHANGED = "EXCHANGE_STATUS_CHANGED"
    const val EXCHANGE_LIST_CHANGED = "EXCHANGE_LIST_CHANGED"
    const val REALTIME_PROBE = "REALTIME_PROBE"
    const val ERROR = "ERROR"
    const val WELCOME = "WELCOME"
}
