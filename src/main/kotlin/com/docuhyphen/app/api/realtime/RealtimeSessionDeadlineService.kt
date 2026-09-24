package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.model.auth.SessionDeadlines
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.websocket.CloseReason
import jakarta.websocket.Session
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@ApplicationScoped
class RealtimeSessionDeadlineService(
    private val registry: RealtimeSessionRegistry,
)
{
    private data class ScheduledDeadline(
        val socket: Session,
        val future: ScheduledFuture<*>,
    )

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val deadlinesBySession = ConcurrentHashMap<UUID, ScheduledDeadline>()

    fun schedule(sessionId: UUID, socket: Session, deadlines: SessionDeadlines)
    {
        val idleFirst = deadlines.sessionExpiresAt == null || deadlines.idleExpiresAt <= deadlines.sessionExpiresAt
        val expiresAt = if (idleFirst) deadlines.idleExpiresAt else deadlines.sessionExpiresAt!!
        val reason = if (idleFirst) RevocationReasonCode.INACTIVITY_TIMEOUT else RevocationReasonCode.SESSION_EXPIRED
        val delayMillis = Duration.between(Instant.now(), expiresAt).toMillis().coerceAtLeast(0)
        val future = scheduler.schedule(
            {
                if (registry.getSocket(sessionId) === socket && socket.isOpen)
                {
                    runCatching {
                        socket.close(
                            CloseReason(
                                { RealtimeEventService.CLOSE_CODE_SESSION_REVOKED },
                                "session revoked: ${reason.name}",
                            )
                        )
                    }
                }
                deadlinesBySession.remove(sessionId)
            },
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
        deadlinesBySession.put(sessionId, ScheduledDeadline(socket, future))?.future?.cancel(false)
    }

    fun reschedule(sessionId: UUID, deadlines: SessionDeadlines)
    {
        val socket = registry.getSocket(sessionId) ?: return
        schedule(sessionId, socket, deadlines)
    }

    fun cancel(sessionId: UUID, socket: Session)
    {
        deadlinesBySession.computeIfPresent(sessionId) { _, scheduled ->
            if (scheduled.socket !== socket) return@computeIfPresent scheduled
            scheduled.future.cancel(false)
            null
        }
    }

    @PreDestroy
    fun stop()
    {
        deadlinesBySession.values.forEach { it.future.cancel(false) }
        deadlinesBySession.clear()
        scheduler.shutdownNow()
    }
}
