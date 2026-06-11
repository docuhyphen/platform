package com.docuhyphen.app.api.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.Session
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-userSession WebSocket bookkeeping. Replaces the old per-userId registry so two devices
 * for the same user no longer overwrite each other.
 *
 *  - `socketsByUserSessionId`: the live socket for a single browser/device session.
 *  - `userSessionIdsByUser`:   reverse lookup, lets revocation broadcasts fan out to all of
 *                               a user's open sockets.
 *  - `subscribers`:             exchange subscriptions, used both for notifications
 *                               and for live "who is viewing" data.
 *  - `exchangesByUserSession`: per-socket subscription set, so socket close cleans up
 *                                     viewer state cheaply.
 */
@ApplicationScoped
class RealtimeSessionRegistry
{
    private val logger = LoggerFactory.getLogger(RealtimeSessionRegistry::class.java)

    private val socketsByUserSessionId = ConcurrentHashMap<UUID, Session>()
    private val userIdByUserSession = ConcurrentHashMap<UUID, UUID>()
    private val userSessionIdsByUser = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    private val subscribers = ConcurrentHashMap<UUID, MutableSet<UUID>>() // exchangeId → userSessionIds
    private val exchangesByUserSession = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    fun addSocket(userSessionId: UUID, appUserId: UUID, session: Session)
    {
        // Replace any stale socket for the same userSession (browser reconnect race).
        socketsByUserSessionId.put(userSessionId, session)?.also { stale ->
            runCatching { if (stale.isOpen) stale.close() }
        }
        userIdByUserSession[userSessionId] = appUserId
        userSessionIdsByUser.computeIfAbsent(appUserId) { ConcurrentHashMap.newKeySet() }.add(userSessionId)
        logger.info("Realtime socket added userSessionId={} appUserId={} totalSockets={}", userSessionId, appUserId, socketsByUserSessionId.size)
    }

    fun removeSocket(userSessionId: UUID)
    {
        socketsByUserSessionId.remove(userSessionId)
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
    }

    fun getSocket(userSessionId: UUID): Session? = socketsByUserSessionId[userSessionId]

    fun getUserId(userSessionId: UUID): UUID? = userIdByUserSession[userSessionId]

    fun getUserSessionsForUser(appUserId: UUID): Set<UUID> =
        userSessionIdsByUser[appUserId]?.toSet() ?: emptySet()

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
