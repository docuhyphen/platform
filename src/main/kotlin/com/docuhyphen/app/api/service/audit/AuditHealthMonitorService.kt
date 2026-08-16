package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.repository.audit.AuditAnalyticsFactRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant

data class AuditHealthReport(
    val outboxBacklogSize: Int,
    val oldestOutboxAgeMinutes: Long?,
    val ledgerEventCount: Long,
    val analyticsFactCount: Long,
    val analyticsProjectionLag: Long,
)

/**
 * Computes health signals that are not already covered
 * by [com.docuhyphen.app.api.service.audit.archive.AuditArchiveScheduler]'s own
 * `AUDIT_ARCHIVE_VERIFICATION_FAILED` marker: outbox backlog age/depth and analytics projection
 * lag. Ledger latency/duplicates/gaps and denied-access anomalies are left for a later pass (see
 * configured archival verification. The service currently focuses on signals that can be derived
 * from persisted audit data.
 */
@ApplicationScoped
class AuditHealthMonitorService @Inject constructor(
    private val auditOutboxRepository: AuditOutboxRepository,
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditAnalyticsFactRepository: AuditAnalyticsFactRepository,
)
{
    fun computeReport(): AuditHealthReport
    {
        val oldestBatch = auditOutboxRepository.findOldestUnledgeredByRecordedAt(1)
        val oldest = oldestBatch.firstOrNull()
        val oldestAgeMinutes = oldest?.let { Duration.between(it.recordedAt.toInstant(), Instant.now()).toMinutes() }

        val backlogSize = auditOutboxRepository.findOldestUnledgeredByRecordedAt(5000).size

        val ledgerCount = auditLedgerEventRepository.countAll()
        val factCount = auditAnalyticsFactRepository.countAll()

        return AuditHealthReport(
            outboxBacklogSize = backlogSize,
            oldestOutboxAgeMinutes = oldestAgeMinutes,
            ledgerEventCount = ledgerCount,
            analyticsFactCount = factCount,
            analyticsProjectionLag = (ledgerCount - factCount).coerceAtLeast(0),
        )
    }
}
