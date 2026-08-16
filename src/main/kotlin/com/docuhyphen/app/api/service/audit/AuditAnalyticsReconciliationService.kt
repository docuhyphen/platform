package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.repository.audit.AuditAnalyticsFactRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

data class AnalyticsReconciliationReport(
    val organizationId: UUID?,
    val platformOnly: Boolean,
    val ledgerEventCount: Long,
    val analyticsFactCount: Long,
    val matches: Boolean,
    val missingLedgerEventIds: List<UUID>,
)

/**
 * Read-only reconciliation of `audit_analytics_fact` against the authoritative
 * `audit_ledger_event` count to reconcile analytical measures against
 * authoritative event counts" gate). Never mutates anything; a caller who wants to fix a mismatch
 * calls [AuditAnalyticsProjector.rebuild] separately.
 */
@ApplicationScoped
class AuditAnalyticsReconciliationService @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditAnalyticsFactRepository: AuditAnalyticsFactRepository,
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditAnalyticsReconciliationService::class.java)
        private const val MISSING_SAMPLE_LIMIT = 20
    }

    fun reconcile(organizationId: UUID?, platformOnly: Boolean, requestedByUserId: UUID?): AnalyticsReconciliationReport
    {
        val ledgerCount = auditLedgerEventRepository.countByOrganization(organizationId, platformOnly)
        val factCount = auditAnalyticsFactRepository.countByOrganization(organizationId, platformOnly)
        val missing = if (factCount < ledgerCount)
        {
            auditAnalyticsFactRepository.findMissingLedgerEventIds(organizationId, platformOnly, MISSING_SAMPLE_LIMIT)
        }
        else
        {
            emptyList()
        }

        val report = AnalyticsReconciliationReport(
            organizationId = organizationId,
            platformOnly = platformOnly,
            ledgerEventCount = ledgerCount,
            analyticsFactCount = factCount,
            matches = ledgerCount == factCount,
            missingLedgerEventIds = missing,
        )
        recordEvent(report, requestedByUserId)
        return report
    }

    private fun recordEvent(report: AnalyticsReconciliationReport, requestedByUserId: UUID?)
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.AUDIT_ANALYTICS_RECONCILED.key,
                    outcome = if (report.matches) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
                    actorId = requestedByUserId,
                    actorKind = if (requestedByUserId == null) AuditActorKind.SYSTEM else AuditActorKind.HUMAN,
                    actorRole = if (requestedByUserId == null) "SYSTEM" else "AUDIT_GOVERNANCE",
                    owner = report.organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    targetType = "AUDIT_ANALYTICS_PROJECTION",
                    payload = mapOf(
                        "ledger_event_count" to report.ledgerEventCount.toString(),
                        "analytics_fact_count" to report.analyticsFactCount.toString(),
                        "matches" to report.matches.toString(),
                    ),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditAnalyticsReconciliationService: AuditRecorder rejected event: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditAnalyticsReconciliationService: AuditRecorder capture failed: {}", e.message, e)
        }
    }
}
