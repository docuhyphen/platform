package com.docuhyphen.app.api.service.realtime

import com.docuhyphen.app.api.model.realtime.RealtimeMessage
import com.docuhyphen.app.api.model.realtime.RealtimeMessageType
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

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
        every = "3600s",
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
