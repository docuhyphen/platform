package com.docuhyphen.app.api.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Active viewers of a exchange,  distinct from subscribers in [RealtimeSessionRegistry]
 * because one user with two tabs counts as one viewer for UI display, but as two subscribers
 * for fan-out.
 */
@ApplicationScoped
class ExchangeViewerRegistry @Inject constructor(
    private val realtimeEventService: RealtimeEventService,
    private val registry: RealtimeSessionRegistry,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeViewerRegistry::class.java)
    }

    // exchangeId → appUserId → set of userSessionIds currently viewing
    private val viewers = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, MutableSet<UUID>>>()

    fun markViewing(exchangeId: UUID, appUserId: UUID, userSessionId: UUID)
    {
        val byUser = viewers.computeIfAbsent(exchangeId) { ConcurrentHashMap() }
        val set = byUser.computeIfAbsent(appUserId) { ConcurrentHashMap.newKeySet() }
        val firstTabForUser = set.isEmpty()
        set.add(userSessionId)
        if (firstTabForUser)
        {
            broadcastViewers(exchangeId)
        }
    }

    fun markNotViewing(exchangeId: UUID, appUserId: UUID, userSessionId: UUID)
    {
        val byUser = viewers[exchangeId] ?: return
        val set = byUser[appUserId] ?: return
        set.remove(userSessionId)
        if (set.isEmpty())
        {
            byUser.remove(appUserId)
            if (byUser.isEmpty()) viewers.remove(exchangeId)
            broadcastViewers(exchangeId)
        }
    }

    /** Clean up all viewer entries owned by a userSession (on socket close). */
    fun cleanupUserSession(userSessionId: UUID)
    {
        viewers.forEach { (exchangeId, byUser) ->
            val changedUsers = mutableListOf<UUID>()
            byUser.forEach { (appUserId, set) ->
                if (set.remove(userSessionId) && set.isEmpty()) changedUsers += appUserId
            }
            changedUsers.forEach { byUser.remove(it) }
            if (changedUsers.isNotEmpty())
            {
                if (byUser.isEmpty()) viewers.remove(exchangeId)
                broadcastViewers(exchangeId)
            }
        }
    }

    fun viewerUserIds(exchangeId: UUID): Set<UUID> =
        viewers[exchangeId]?.keys?.toSet() ?: emptySet()

    private fun broadcastViewers(exchangeId: UUID)
    {
        val viewerIds = viewerUserIds(exchangeId).map { it.toString() }
        val message = RealtimeMessage(
            type = RealtimeMessageType.SHARING_VIEWERS,
            exchangeId = exchangeId.toString(),
            viewerUserIds = viewerIds,
        )
        realtimeEventService.broadcastToExchange(exchangeId, message)
    }
}
