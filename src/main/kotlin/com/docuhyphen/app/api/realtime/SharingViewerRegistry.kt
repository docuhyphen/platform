package com.docuhyphen.app.api.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Active viewers of a sharing session,  distinct from subscribers in [RealtimeSessionRegistry]
 * because one user with two tabs counts as one viewer for UI display, but as two subscribers
 * for fan-out.
 */
@ApplicationScoped
class SharingViewerRegistry @Inject constructor(
    private val realtimeEventService: RealtimeEventService,
    private val registry: RealtimeSessionRegistry,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingViewerRegistry::class.java)
    }

    // sharingSessionId → appUserId → set of userSessionIds currently viewing
    private val viewers = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, MutableSet<UUID>>>()

    fun markViewing(sharingSessionId: UUID, appUserId: UUID, userSessionId: UUID)
    {
        val byUser = viewers.computeIfAbsent(sharingSessionId) { ConcurrentHashMap() }
        val set = byUser.computeIfAbsent(appUserId) { ConcurrentHashMap.newKeySet() }
        val firstTabForUser = set.isEmpty()
        set.add(userSessionId)
        if (firstTabForUser)
        {
            broadcastViewers(sharingSessionId)
        }
    }

    fun markNotViewing(sharingSessionId: UUID, appUserId: UUID, userSessionId: UUID)
    {
        val byUser = viewers[sharingSessionId] ?: return
        val set = byUser[appUserId] ?: return
        set.remove(userSessionId)
        if (set.isEmpty())
        {
            byUser.remove(appUserId)
            if (byUser.isEmpty()) viewers.remove(sharingSessionId)
            broadcastViewers(sharingSessionId)
        }
    }

    /** Clean up all viewer entries owned by a userSession (on socket close). */
    fun cleanupUserSession(userSessionId: UUID)
    {
        viewers.forEach { (sharingSessionId, byUser) ->
            val changedUsers = mutableListOf<UUID>()
            byUser.forEach { (appUserId, set) ->
                if (set.remove(userSessionId) && set.isEmpty()) changedUsers += appUserId
            }
            changedUsers.forEach { byUser.remove(it) }
            if (changedUsers.isNotEmpty())
            {
                if (byUser.isEmpty()) viewers.remove(sharingSessionId)
                broadcastViewers(sharingSessionId)
            }
        }
    }

    fun viewerUserIds(sharingSessionId: UUID): Set<UUID> =
        viewers[sharingSessionId]?.keys?.toSet() ?: emptySet()

    private fun broadcastViewers(sharingSessionId: UUID)
    {
        val viewerIds = viewerUserIds(sharingSessionId).map { it.toString() }
        val message = RealtimeMessage(
            type = RealtimeMessageType.SHARING_VIEWERS,
            sharingSessionId = sharingSessionId.toString(),
            viewerUserIds = viewerIds,
        )
        realtimeEventService.broadcastToSharingSession(sharingSessionId, message)
    }
}
