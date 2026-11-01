package com.docuhyphen.app.api.service.audit

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

@ApplicationScoped
class AuditHealthMonitorScheduler @Inject constructor(
    private val auditHealthMonitorService: AuditHealthMonitorService,

    @ConfigProperty(name = "app.audit.health.outbox-max-age-minutes", defaultValue = "30")
    private val outboxMaxAgeMinutes: Long,

    @ConfigProperty(name = "app.audit.health.analytics-lag-threshold", defaultValue = "1000")
    private val analyticsLagThreshold: Long,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditHealthMonitorScheduler::class.java)
        const val OUTBOX_BACKLOG_MARKER = "AUDIT_OUTBOX_BACKLOG_HIGH"
        const val ANALYTICS_LAG_MARKER = "AUDIT_ANALYTICS_PROJECTION_LAG_HIGH"
    }

    @Scheduled(
        every = "\${app.audit.health.check-every:5m}",
        identity = "audit-health-monitor",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    fun tick()
    {
        try
        {
            val report = auditHealthMonitorService.computeReport()

            val age = report.oldestOutboxAgeMinutes
            if (age != null && age >= outboxMaxAgeMinutes)
            {
                logger.error(
                    "{}: backlogSize={} oldestAgeMinutes={} thresholdMinutes={}",
                    OUTBOX_BACKLOG_MARKER, report.outboxBacklogSize, age, outboxMaxAgeMinutes,
                )
            }

            if (report.analyticsProjectionLag >= analyticsLagThreshold)
            {
                logger.error(
                    "{}: lag={} ledgerEventCount={} analyticsFactCount={} threshold={}",
                    ANALYTICS_LAG_MARKER, report.analyticsProjectionLag, report.ledgerEventCount,
                    report.analyticsFactCount, analyticsLagThreshold,
                )
            }
        }
        catch (e: Exception)
        {
            logger.error("AuditHealthMonitorScheduler tick failed: {}", e.message, e)
        }
    }
}
