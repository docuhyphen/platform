package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.repository.AuditExportRepository
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Periodically builds queued `BUILDING` [com.docuhyphen.app.api.model.entity.AuditExport] rows
 * ([AuditExportBuilder]) and expires `READY` exports past their download window. Building happens off the approval request/response so
 * an approver's HTTP call never blocks on ledger verification + archive I/O.
 *
 * [ActivateRequestContext] opens a request context for the duration of each tick, exactly like
 * [com.docuhyphen.app.api.service.audit.archive.AuditArchiveScheduler.verifyTick], because
 * [com.docuhyphen.app.api.service.audit.AuditRecorder] is `@RequestScoped`.
 */
@ApplicationScoped
class AuditExportScheduler @Inject constructor(
    private val auditExportRepository: AuditExportRepository,
    private val auditExportService: AuditExportService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditExportScheduler::class.java)
    }

    @Scheduled(
        every = "\${app.audit.export.build-every:1m}",
        identity = "audit-export-build",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun buildTick()
    {
        try
        {
            val pending = auditExportRepository.findByStatus(AuditExportStatus.BUILDING)
            for (export in pending)
            {
                auditExportService.processBuilding(export)
            }
        }
        catch (e: Exception)
        {
            logger.error("audit export build tick failed: {}", e.message, e)
        }
    }

    @Scheduled(
        every = "\${app.audit.export.expire-every:15m}",
        identity = "audit-export-expire",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun expireTick()
    {
        try
        {
            val expired = auditExportService.expireDue()
            if (expired > 0)
            {
                logger.info("audit export expire tick: expired={}", expired)
            }
        }
        catch (e: Exception)
        {
            logger.error("audit export expire tick failed: {}", e.message, e)
        }
    }
}
