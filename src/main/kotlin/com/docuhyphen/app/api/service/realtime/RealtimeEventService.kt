package com.docuhyphen.app.api.service.realtime

import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.realtime.RealtimeMessage
import com.docuhyphen.app.api.model.realtime.RealtimeMessageType
import com.docuhyphen.app.api.model.realtime.UserSessionInfo
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.CloseReason
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class RealtimeEventService @Inject constructor(
    private val registry: RealtimeSessionRegistry,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeEventService::class.java)
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        const val CLOSE_CODE_SESSION_REVOKED = 4001
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

    fun broadcastToAll(message: RealtimeMessage)
    {
        registry.allUserSessionIds().forEach { sendToUserSession(it, message) }
    }

    fun broadcastNotificationToUser(appUserId: UUID, notification: NotificationDto)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(type = RealtimeMessageType.NOTIFICATION, notification = notification)
        )
    }

    fun closeSessionSocket(userSessionId: UUID)
    {
        registry.getSocket(userSessionId)?.let { socket ->
            runCatching {
                if (socket.isOpen)
                {
                    socket.close(CloseReason({ CLOSE_CODE_SESSION_REVOKED }, "signed out"))
                }
            }
        }
    }

    fun notifySessionRevoked(userSessionId: UUID, reason: String)
    {
        sendToUserSession(
            userSessionId,
            RealtimeMessage(type = RealtimeMessageType.SESSION_REVOKED, reason = reason)
        )
        registry.getSocket(userSessionId)?.let { socket ->
            runCatching {
                if (socket.isOpen)
                {
                    socket.close(
                        CloseReason(
                            { CLOSE_CODE_SESSION_REVOKED },
                            "session revoked: $reason"
                        )
                    )
                }
            }
        }
    }

    fun notifyAllSessionsRevoked(appUserId: UUID, reason: String, exceptUserSessionId: UUID? = null)
    {
        registry.getUserSessionsForUser(appUserId).forEach { userSessionId ->
            if (userSessionId != exceptUserSessionId) notifySessionRevoked(userSessionId, reason)
        }
    }

    fun notifySignedOutOtherDevice(appUserId: UUID, exceptUserSessionId: UUID? = null)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(type = RealtimeMessageType.SIGNED_OUT_OTHER_DEVICE),
            exceptUserSessionId,
        )
    }

    fun notifySessionCreated(appUserId: UUID, session: UserSessionInfo, newUserSessionId: UUID)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(type = RealtimeMessageType.SESSION_CREATED, session = session),
            exceptUserSessionId = newUserSessionId,
        )
    }

    fun notifySessionRemoved(appUserId: UUID, removedUserSessionId: UUID, exceptUserSessionId: UUID? = null)
    {
        broadcastToUser(
            appUserId,
            RealtimeMessage(
                type = RealtimeMessageType.SESSION_REMOVED,
                userSessionId = removedUserSessionId.toString(),
            ),
            exceptUserSessionId,
        )
    }
}
