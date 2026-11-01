package com.docuhyphen.app.api.service.realtime

import com.docuhyphen.app.api.model.realtime.RealtimeMessage
import com.docuhyphen.app.api.model.realtime.RealtimeMessageType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
