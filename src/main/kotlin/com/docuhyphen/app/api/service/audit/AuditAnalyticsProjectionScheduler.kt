package com.docuhyphen.app.api.service.audit

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class AuditAnalyticsProjectionScheduler @Inject constructor(
    private val auditAnalyticsProjector: AuditAnalyticsProjector,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditAnalyticsProjectionScheduler::class.java)
    }

    @Scheduled(every = "\${app.audit.analytics.project-every:1m}", identity = "audit-analytics-projection", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun tick()
    {
        try
        {
            val result = auditAnalyticsProjector.project()
            if (result.failed > 0)
            {
                logger.warn(
                    "AUDIT_ANALYTICS_PROJECTION_FAILURES projected={} alreadyProjected={} failed={}",
                    result.projected, result.alreadyProjected, result.failed,
                )
            }
        }
        catch (e: Exception)
        {
            logger.error("AuditAnalyticsProjectionScheduler tick failed: {}", e.message, e)
        }
    }
}
