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
    /** Always false when broadcast to other devices — the new session is not their session. */
    val isCurrent: Boolean = false,
)

/**
 * Wire protocol envelope for the /realtime/{userSessionId} WebSocket.
 *
 * Direction is implicit from `type`:
 *
 *   Client → Server
 *     - PING
 *     - SUBSCRIBE_SHARING_SESSION      { sharingSessionId }
 *     - UNSUBSCRIBE_SHARING_SESSION    { sharingSessionId }
 *
 *   Server → Client
 *     - PONG                            { serverTime }
 *     - SESSION_REVOKED                 { reason }          ← this device is kicked
 *     - SESSION_CREATED                 { session }         ← another device signed in
 *     - SESSION_REMOVED                 { userSessionId }   ← another device's session ended
 *     - PASSWORD_CHANGED
 *     - SIGNED_OUT_OTHER_DEVICE
 *     - NOTIFICATION                    { notification }
 *     - PRESENCE_UPDATE                 { userId, online }
 *     - SHARING_VIEWERS                 { sharingSessionId, viewerUserIds }
 *     - ERROR                           { code, message }
 *     - WELCOME                         { userSessionId, serverTime }
 */
@Serializable
data class RealtimeMessage(
    val type: String,
    val sharingSessionId: String? = null,
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
)

object RealtimeMessageType
{
    // Client → Server
    const val PING = "PING"
    const val SUBSCRIBE_SHARING_SESSION = "SUBSCRIBE_SHARING_SESSION"
    const val UNSUBSCRIBE_SHARING_SESSION = "UNSUBSCRIBE_SHARING_SESSION"

    // Server → Client
    const val PONG = "PONG"
    const val SESSION_REVOKED = "SESSION_REVOKED"
    const val SESSION_CREATED = "SESSION_CREATED"
    const val SESSION_REMOVED = "SESSION_REMOVED"
    const val PASSWORD_CHANGED = "PASSWORD_CHANGED"
    const val SIGNED_OUT_OTHER_DEVICE = "SIGNED_OUT_OTHER_DEVICE"
    const val NOTIFICATION = "NOTIFICATION"
    const val PRESENCE_UPDATE = "PRESENCE_UPDATE"
    const val SHARING_VIEWERS = "SHARING_VIEWERS"
    const val ERROR = "ERROR"
    const val WELCOME = "WELCOME"
}
