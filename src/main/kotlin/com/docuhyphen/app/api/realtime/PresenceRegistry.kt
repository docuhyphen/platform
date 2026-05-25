package com.docuhyphen.app.api.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks which users are online at process level, derived from open realtime sockets.
 *
 * A user is "online" while ≥1 of their userSessions has an active socket. Tab close on one
 * device must not turn them offline if another tab is still connected — hence the refcount.
 *
 * Multi-node note: this is in-process. For multi-node presence we'd back this with Redis
 * (HSET `presence:{userId}` socketId 1; `EXPIRE` per heartbeat; pub/sub for fan-out). Kept
 * in-process here to match the rest of the realtime layer; promote later when we shard.
 */
@ApplicationScoped
class PresenceRegistry @Inject constructor(
    private val realtimeEventService: RealtimeEventService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PresenceRegistry::class.java)
    }

    private val socketCounts = ConcurrentHashMap<UUID, AtomicInteger>()

    fun markOnline(appUserId: UUID): Boolean
    {
        val count = socketCounts.computeIfAbsent(appUserId) { AtomicInteger(0) }.incrementAndGet()
        val justCameOnline = count == 1
        if (justCameOnline)
        {
            logger.info("Presence: user came online appUserId={}", appUserId)
            broadcastPresence(appUserId, online = true)
        }
        return justCameOnline
    }

    fun markOffline(appUserId: UUID): Boolean
    {
        val counter = socketCounts[appUserId] ?: return false
        val count = counter.decrementAndGet()
        return when
        {
            count <= 0 ->
            {
                socketCounts.remove(appUserId)
                logger.info("Presence: user went offline appUserId={}", appUserId)
                broadcastPresence(appUserId, online = false)
                true
            }
            else -> false
        }
    }

    fun isOnline(appUserId: UUID): Boolean = (socketCounts[appUserId]?.get() ?: 0) > 0

    fun onlineUserIds(): Set<UUID> = socketCounts.keys.toSet()

    private fun broadcastPresence(appUserId: UUID, online: Boolean)
    {
        // Fan out to everyone currently connected. Tighter scoping (e.g. org-only) can be
        // layered on later by querying the user's organization and broadcasting per-org.
        val message = RealtimeMessage(
            type = RealtimeMessageType.PRESENCE_UPDATE,
            userId = appUserId.toString(),
            online = online,
        )
        socketCounts.keys.forEach { otherUserId ->
            if (otherUserId != appUserId) realtimeEventService.broadcastToUser(otherUserId, message)
        }
    }
}
