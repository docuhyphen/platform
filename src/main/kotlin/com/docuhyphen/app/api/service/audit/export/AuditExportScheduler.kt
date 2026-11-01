package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.repository.audit.AuditExportRepository
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

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
