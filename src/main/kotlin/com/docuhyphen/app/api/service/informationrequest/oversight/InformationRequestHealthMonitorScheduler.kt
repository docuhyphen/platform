package com.docuhyphen.app.api.service.informationrequest.oversight

import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicator
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class InformationRequestHealthMonitorScheduler @Inject constructor(
    private val healthService: InformationRequestHealthService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(InformationRequestHealthMonitorScheduler::class.java)
        const val BREACH_MARKER = "INFORMATION_REQUEST_HEALTH_BREACH"
    }

    @Scheduled(
        every = "\${app.information-request.health.every:15m}",
        identity = "information-request-health-monitor",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun tick()
    {
        check()
    }

    fun check(): List<InformationRequestHealthIndicator>
    {
        return try
        {
            val breaches = healthService.report().indicators.filter { it.breached }
            breaches.forEach { indicator ->
                logger.error(
                    "{}: indicator={} count={} threshold={}",
                    BREACH_MARKER, indicator.key, indicator.count, indicator.threshold,
                )
            }
            breaches
        }
        catch (exception: Exception)
        {
            logger.error("Information Request health monitor tick failed", exception)
            emptyList()
        }
    }
}
