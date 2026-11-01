package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditHealthReport
import com.docuhyphen.app.api.repository.audit.AuditAnalyticsFactRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant

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
