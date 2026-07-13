package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Periodically builds queued `BUILDING` [com.docuhyphen.app.api.model.entity.AuditExport] rows
 * ([AuditExportBuilder]) and expires `READY` exports past their download window. Building happens off the approval request/response so
 * an approver's HTTP call never blocks on ledger verification + archive I/O.
 *
 * More than one application node may run this scheduler at once. Each node claims a candidate
 * export's build-claim lease ([AuditExportService.claimForBuilding]) under [workerId] - a value
 * unique to this scheduler instance - before building it, so two nodes never perform the same
 * export's archive I/O concurrently.
 *
 * [ActivateRequestContext] opens a request context for the duration of each tick, exactly like
 * [com.docuhyphen.app.api.service.audit.archive.AuditArchiveScheduler.verifyTick], because
 * [com.docuhyphen.app.api.service.audit.AuditRecorder] is `@RequestScoped`.
 */
@ApplicationScoped
class AuditExportScheduler @Inject constructor(
    private val auditExportRepository: AuditExportRepository,
    private val auditExportService: AuditExportService,
    private val configService: AuditExportConfigService,
)
{
    private val workerId: String = UUID.randomUUID().toString()

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
            val leaseDuration = Duration.ofMinutes(configService.getBuildLeaseMinutes().toLong())
            val pending = auditExportRepository.findDueForBuilding(Timestamp.from(Instant.now()))
            for (candidate in pending)
            {
                val claimed = auditExportService.claimForBuilding(candidate.id, workerId, leaseDuration) ?: continue
                auditExportService.processBuilding(claimed)
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
