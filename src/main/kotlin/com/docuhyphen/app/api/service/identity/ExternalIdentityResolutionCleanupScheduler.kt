package com.docuhyphen.app.api.service.identity

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class ExternalIdentityResolutionCleanupScheduler @Inject constructor(
    private val resolutionService: ExternalIdentityResolutionService,
)
{
    @Scheduled(
        every = "\${app.organization-trust.identity-resolution-cleanup-every:1h}",
        identity = "external-identity-resolution-cleanup",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun cleanupTick()
    {
        try
        {
            val removed = resolutionService.cleanupExpired()
            if (removed > 0)
            {
                logger.info("external identity resolution cleanup tick: removed={}", removed)
            }
        }
        catch (exception: Exception)
        {
            logger.error("external identity resolution cleanup tick failed: {}", exception.message, exception)
        }
    }

    companion object
    {
        private val logger = LoggerFactory.getLogger(ExternalIdentityResolutionCleanupScheduler::class.java)
    }
}
