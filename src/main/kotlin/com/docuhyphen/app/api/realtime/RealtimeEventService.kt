package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.model.dto.NotificationDto
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.CloseReason
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Central send-side API for the realtime channel. Callers (auth, sharing, comments, etc.)
 * stay decoupled from raw `jakarta.websocket.Session` and from the registry.
 */
@ApplicationScoped
class RealtimeEventService @Inject constructor(
    private val registry: RealtimeSessionRegistry,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeEventService::class.java)
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        // Reserved close codes 4000-4999 are app-defined.
        const val CLOSE_CODE_EXCHANGE_REVOKED = 4001
    }

    fun sendToUserSession(userSessionId: UUID, message: RealtimeMessage)
    {
        val socket = registry.getSocket(userSessionId) ?: return
        if (!socket.isOpen) return
        runCatching {
            socket.basicRemote.sendText(json.encodeToString(message))
        }.onFailure { e ->
            logger.warn("Failed to send realtime message to userSessionId={} type={}", userSessionId, message.type, e)
        }
    }

    fun broadcastToUser(appUserId: UUID, message: RealtimeMessage, exceptUserSessionId: UUID? = null)
    {
        registry.getUserSessionsForUser(appUserId).forEach { userSessionId ->
            if (userSessionId != exceptUserSessionId) sendToUserSession(userSessionId, message)
        }
    }

    fun broadcastToExchange(exchangeId: UUID, message: RealtimeMessage, exceptUserSessionId: UUID? = null)
    {
        registry.exchangeSubscribers(exchangeId).forEach { userSessionId ->
            if (userSessionId != exceptUserSessionId) sendToUserSession(userSessionId, message)
        }
    }

    fun broadcastNotificationToUser(appUserId: UUID, notification: NotificationDto)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(type = RealtimeMessageType.NOTIFICATION, notification = notification)
        )
    }

    /** Push EXCHANGE_REVOKED and close the socket for one specific device. */
    fun notifySessionRevoked(userSessionId: UUID, reason: String)
    {
        sendToUserSession(
            userSessionId,
            RealtimeMessage(type = RealtimeMessageType.EXCHANGE_REVOKED, reason = reason)
        )
        registry.getSocket(userSessionId)?.let { socket ->
            runCatching {
                if (socket.isOpen)
                {
                    socket.close(
                        CloseReason(
                            { CLOSE_CODE_EXCHANGE_REVOKED },
                            "session revoked: $reason"
                        )
                    )
                }
            }
        }
    }

    /** Push EXCHANGE_REVOKED to every active socket for an app user. */
    fun notifyAllSessionsRevoked(appUserId: UUID, reason: String, exceptUserSessionId: UUID? = null)
    {
        registry.getUserSessionsForUser(appUserId).forEach { userSessionId ->
            if (userSessionId != exceptUserSessionId) notifySessionRevoked(userSessionId, reason)
        }
    }

    /** Tell other devices of the same user that the current device just signed out. */
    fun notifySignedOutOtherDevice(appUserId: UUID, exceptUserSessionId: UUID? = null)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(type = RealtimeMessageType.SIGNED_OUT_OTHER_DEVICE),
            exceptUserSessionId,
        )
    }

    /** Tell existing devices that a new session was just created (new login elsewhere). */
    fun notifySessionCreated(appUserId: UUID, session: UserSessionInfo, newUserSessionId: UUID)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(type = RealtimeMessageType.EXCHANGE_CREATED, session = session),
            exceptUserSessionId = newUserSessionId,
        )
    }

    /** Tell existing devices that one of their peer sessions was removed. */
    fun notifySessionRemoved(appUserId: UUID, removedUserSessionId: UUID, exceptUserSessionId: UUID? = null)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(
                type = RealtimeMessageType.EXCHANGE_REMOVED,
                userSessionId = removedUserSessionId.toString(),
            ),
            exceptUserSessionId,
        )
    }
}
