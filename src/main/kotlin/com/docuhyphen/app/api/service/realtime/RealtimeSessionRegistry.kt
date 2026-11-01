package com.docuhyphen.app.api.service.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class RealtimeSessionRegistry
{
    private val logger = LoggerFactory.getLogger(RealtimeSessionRegistry::class.java)

    private val socketsByUserSessionId = ConcurrentHashMap<UUID, Session>()
    private val userIdByUserSession = ConcurrentHashMap<UUID, UUID>()
    private val userSessionIdsByUser = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    private val subscribers = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val exchangesByUserSession = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    fun addSocket(userSessionId: UUID, appUserId: UUID, session: Session)
    {
        socketsByUserSessionId.put(userSessionId, session)?.also { stale ->
            runCatching { if (stale.isOpen) stale.close() }
        }
        userIdByUserSession[userSessionId] = appUserId
        userSessionIdsByUser.computeIfAbsent(appUserId) { ConcurrentHashMap.newKeySet() }.add(userSessionId)
        logger.info(
            "Realtime socket added userSessionId={} appUserId={} totalSockets={}",
            userSessionId,
            appUserId,
            socketsByUserSessionId.size
        )
    }

    fun removeSocket(userSessionId: UUID, session: Session): Boolean
    {
        if (!socketsByUserSessionId.remove(userSessionId, session)) return false
        val appUserId = userIdByUserSession.remove(userSessionId)
        if (appUserId != null)
        {
            userSessionIdsByUser[appUserId]?.let { set ->
                set.remove(userSessionId)
                if (set.isEmpty()) userSessionIdsByUser.remove(appUserId)
            }
        }
        exchangesByUserSession.remove(userSessionId)?.forEach { exchangeId ->
            subscribers[exchangeId]?.remove(userSessionId)
        }
        logger.info("Realtime socket removed userSessionId={}", userSessionId)
        return true
    }

    fun getSocket(userSessionId: UUID): Session? = socketsByUserSessionId[userSessionId]

    fun getUserId(userSessionId: UUID): UUID? = userIdByUserSession[userSessionId]

    fun getUserSessionsForUser(appUserId: UUID): Set<UUID> =
        userSessionIdsByUser[appUserId]?.toSet() ?: emptySet()

    fun allUserSessionIds(): Set<UUID> = socketsByUserSessionId.keys.toSet()

    fun isUserOnline(appUserId: UUID): Boolean =
        userSessionIdsByUser[appUserId]?.isNotEmpty() == true

    fun snapshotOnlineUserIds(): Set<UUID> = userSessionIdsByUser.keys.toSet()

    fun subscribeToExchange(userSessionId: UUID, exchangeId: UUID)
    {
        subscribers.computeIfAbsent(exchangeId) { ConcurrentHashMap.newKeySet() }.add(userSessionId)
        exchangesByUserSession.computeIfAbsent(userSessionId) { ConcurrentHashMap.newKeySet() }.add(exchangeId)
    }

    fun unsubscribeFromExchange(userSessionId: UUID, exchangeId: UUID)
    {
        subscribers[exchangeId]?.remove(userSessionId)
        exchangesByUserSession[userSessionId]?.remove(exchangeId)
    }

    fun exchangeSubscribers(exchangeId: UUID): Set<UUID> =
        subscribers[exchangeId]?.toSet() ?: emptySet()
}
