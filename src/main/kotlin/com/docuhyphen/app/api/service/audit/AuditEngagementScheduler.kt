package com.docuhyphen.app.api.service.audit

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class AuditEngagementScheduler @Inject constructor(
    private val auditEngagementService: AuditEngagementService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditEngagementScheduler::class.java)
    }

    @Scheduled(
        every = "\${app.audit.engagement.expire-every:5m}",
        identity = "audit-engagement-expire",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun expireTick()
    {
        try
        {
            val expired = auditEngagementService.expireDue()
            if (expired > 0)
            {
                logger.info("audit engagement expire tick: expired={}", expired)
            }
        }
        catch (e: Exception)
        {
            logger.error("audit engagement expire tick failed: {}", e.message, e)
        }
    }
}
