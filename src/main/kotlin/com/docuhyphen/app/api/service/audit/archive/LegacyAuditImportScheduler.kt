package com.docuhyphen.app.api.service.audit.archive

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Periodically drains [LegacyAuditImportService] in small batches until no legacy rows remain
 * (each tick simply finds fewer or zero candidates once caught up, so this naturally becomes a
 * no-op rather than needing a separate "done" flag). Runs relatively infrequently since this is
 * one-time historical backfill, not steady-state capture.
 */
@ApplicationScoped
class LegacyAuditImportScheduler @Inject constructor(
    private val legacyAuditImportService: LegacyAuditImportService,
)
{
    private val logger = LoggerFactory.getLogger(LegacyAuditImportScheduler::class.java)

    @Scheduled(
        every = "\${app.audit.archive.legacy-import-every:15m}",
        identity = "audit-legacy-import",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick()
    {
        try
        {
            val result = legacyAuditImportService.importBatch()
            if (result.imported > 0 || result.failed > 0)
            {
                logger.info(
                    "legacy audit import pass: imported={} skipped={} failed={}",
                    result.imported, result.skipped, result.failed,
                )
            }
        }
        catch (e: Exception)
        {
            logger.error("legacy audit import pass failed: {}", e.message, e)
        }
    }
}
