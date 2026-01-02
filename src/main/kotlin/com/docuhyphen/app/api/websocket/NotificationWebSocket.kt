package com.docuhyphen.app.api.websocket

import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.serializer.JsonWebSocketSerializer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.websocket.*
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

data class SubscriptionMessage(
    val type: String,
    val sessionId: String
)

@Singleton
class WebSocketSessionRegistry
{
    private val logger = LoggerFactory.getLogger(WebSocketSessionRegistry::class.java)
    private val userSessions = ConcurrentHashMap<String, Session>()
    private val sessionSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    init
    {
        logger.info("WebSocketSessionRegistry created")
    }

    fun addSession(userId: String, session: Session)
    {
        userSessions[userId] = session
        logger.info("Added session for user $userId. Total sessions: ${userSessions.size}")
    }

    fun removeSession(userId: String)
    {
        userSessions.remove(userId)
        logger.info("Removed session for user $userId. Total sessions: ${userSessions.size}")
    }

    fun getSession(userId: String): Session?
    {
        return userSessions[userId]
    }

    fun subscribeUserToSession(userId: String, sessionId: String)
    {
        sessionSubscriptions.computeIfAbsent(sessionId) { ConcurrentHashMap.newKeySet() }.add(userId)
        logger.info("User $userId subscribed to session $sessionId")
    }

    fun removeUserFromAllSubscriptions(userId: String)
    {
        sessionSubscriptions.forEach { (_, subscribers) ->
            subscribers.remove(userId)
        }
        logger.info("Removed user $userId from all subscriptions")
    }

    fun getSessionSubscribers(sessionId: String): Set<String>
    {
        return sessionSubscriptions[sessionId] ?: emptySet()
    }

    fun getAllSessions(): Map<String, Session>
    {
        return userSessions.toMap()
    }
}

@ServerEndpoint(
    value = "/notifications/{userId}",
    encoders = [JsonWebSocketSerializer::class],
    decoders = [JsonWebSocketSerializer::class]
)
@ApplicationScoped
class NotificationWebSocket
{
    private val logger = LoggerFactory.getLogger(NotificationWebSocket::class.java)

    @Inject
    private lateinit var sessionRegistry: WebSocketSessionRegistry

    init
    {
        logger.info("NotificationWebSocket instance created")
    }

    @OnOpen
    fun onOpen(session: Session, @PathParam("userId") userId: String)
    {
        logger.info("WebSocket connection opened for user: $userId")
        sessionRegistry.addSession(userId, session)
        logger.info("User session added to registry")
    }

    @OnClose
    fun onClose(session: Session, @PathParam("userId") userId: String)
    {
        logger.info("WebSocket connection closed for user: $userId")
        sessionRegistry.removeSession(userId)
        sessionRegistry.removeUserFromAllSubscriptions(userId)
    }

    @OnError
    fun onError(session: Session, @PathParam("userId") userId: String, throwable: Throwable)
    {
        logger.error("WebSocket error for user: $userId", throwable)
    }

    @OnMessage
    fun onMessage(message: String, @PathParam("userId") userId: String)
    {
        try
        {
            val subscriptionMessage = Json.decodeFromString<SubscriptionMessage>(message)

            if (subscriptionMessage.type == "SUBSCRIBE")
            {
                val sessionId = subscriptionMessage.sessionId
                logger.info("User $userId subscribing to session $sessionId")
                sessionRegistry.subscribeUserToSession(userId, sessionId)
            }
        }
        catch (e: Exception)
        {
            logger.error("Error processing WebSocket message from user $userId", e)
        }
    }

    fun broadcastToSession(sessionId: String, notification: NotificationDto)
    {
        val subscribers = sessionRegistry.getSessionSubscribers(sessionId)
        logger.info("Broadcasting notification to session $sessionId with ${subscribers.size} subscribers")

        val jsonMessage = Json.encodeToString(notification)
        for (userId in subscribers)
        {
            val session = sessionRegistry.getSession(userId)
            if (session != null && session.isOpen)
            {
                try
                {
                    session.basicRemote.sendText(jsonMessage)
                }
                catch (e: Exception)
                {
                    logger.error("Failed to send notification to user $userId", e)
                }
            }
        }
    }

    fun broadcastToUser(userId: String, notification: NotificationDto)
    {
        logger.info("Attempting to broadcast to user: $userId")
        val session = sessionRegistry.getSession(userId)
        if (session != null && session.isOpen)
        {
            try
            {
                val jsonMessage = Json.encodeToString(notification)
                session.basicRemote.sendText(jsonMessage)
                logger.info("Successfully sent notification to user $userId")
            }
            catch (e: Exception)
            {
                logger.error("Failed to send notification to user $userId", e)
            }
        }
        else
        {
            logger.warn("Cannot send notification to user $userId: ${if (session == null) "No session found" else "Session closed"}")
        }
    }
}