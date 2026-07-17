package com.docuhyphen.app.api.service.organization

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class OrganizationTrustRequestExpiryScheduler @Inject constructor(
    private val relationshipService: OrganizationTrustRelationshipService,
    private val notificationService: OrganizationTrustNotificationService,
)
{
    @Scheduled(
        every = "\${app.organization-trust.expire-every:5m}",
        identity = "organization-trust-request-expiry",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun expireTick()
    {
        try
        {
            val expired = relationshipService.expireDueRequestRecords()
            expired.forEach { relationship ->
                notificationService.publish(
                    com.docuhyphen.app.api.service.audit.catalog.AuditEventType.ORG_TRUST_EXPIRED,
                    relationship,
                )
            }
            if (expired.isNotEmpty())
            {
                logger.info("organization trust request expiry tick: expired={}", expired.size)
            }
        }
        catch (exception: Exception)
        {
            logger.error("organization trust request expiry tick failed: {}", exception.message, exception)
        }
    }

    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationTrustRequestExpiryScheduler::class.java)
    }
}
