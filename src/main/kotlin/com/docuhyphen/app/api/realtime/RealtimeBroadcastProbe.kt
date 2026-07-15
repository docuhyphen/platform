package com.docuhyphen.app.api.realtime

import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Disposable local-development probe for validating server-to-client realtime delivery.
 * Delete this class and the REALTIME_PROBE frontend handler when verification is complete.
 */
@ApplicationScoped
@IfBuildProfile("local")
class RealtimeBroadcastProbe @Inject constructor(
    private val realtimeEventService: RealtimeEventService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeBroadcastProbe::class.java)
    }

    @Scheduled(
        every = "600s",
        identity = "realtime-broadcast-probe",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun broadcast()
    {
        val timestamp = System.currentTimeMillis()
        logger.info("Broadcasting disposable realtime probe timestamp={}", timestamp)
        realtimeEventService.broadcastToAll(
            RealtimeMessage(
                type = RealtimeMessageType.REALTIME_PROBE,
                message = "Realtime probe received at $timestamp",
                serverTime = timestamp,
            ),
        )
    }
}
