package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.repository.AuthAuditEventRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

data class LegacyImportResult(val imported: Int, val skipped: Int, val failed: Int)

/**
 * Phase 4 task 5 of `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`: one-time/idempotent import of
 * pre-recorder `auth_audit_event` rows onto the ledger, so history predating Phase 3's dual
 * write is not silently absent from the archive/verification pipeline this phase builds.
 *
 * Every imported row is captured as [AuditEventType.LEGACY_AUDIT_EVENT_IMPORTED] - never
 * re-mapped onto the row's original per-action event type - specifically so it can never be
 * mistaken for a recorder-captured occurrence with full end-to-end provenance (server trace id,
 * correlation id, etc. were never captured for these rows); the original action/outcome/event
 * hash are preserved verbatim in the payload for traceability. Idempotent by construction: the
 * eventId/idempotencyKey are derived from the legacy row's stable id, and
 * [com.docuhyphen.app.api.repository.AuthAuditEventRepository.findLegacyUnimported] only ever
 * returns rows with no matching `audit_ledger_event`, so re-running this after a partial failure
 * only processes what is still missing.
 *
 * **Known, documented scope limitation**: only `auth_audit_event` (the
 * [com.docuhyphen.app.api.service.auth.AuthAuditService] legacy table) is imported here. Legacy
 * `document_audit_log` rows are not imported by this session - unlike `auth_audit_event`,
 * `ExchangeDocumentAuditService`'s dual write does not reuse the original row's id as the ledger
 * `eventId` (see `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`'s Phase 3 handoff), so there is no cheap,
 * reliable identity to match "already imported" against without a deeper investigation; left as a
 * genuine gap for a future session rather than risking duplicate ledger events.
 */
@ApplicationScoped
class LegacyAuditImportService @Inject constructor(
    private val authAuditEventRepository: AuthAuditEventRepository,
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(LegacyAuditImportService::class.java)
    }

    fun importBatch(batchSize: Int = 200): LegacyImportResult
    {
        val rows = authAuditEventRepository.findLegacyUnimported(batchSize)
        var imported = 0
        var skipped = 0
        var failed = 0

        for (row in rows)
        {
            try
            {
                auditRecorder.record(
                    AuditEventDraft(
                        eventTypeKey = AuditEventType.LEGACY_AUDIT_EVENT_IMPORTED.key,
                        outcome = AuthAuditService.mapLegacyOutcome(row.outcome),
                        actorId = row.actorId,
                        actorRole = row.actorRole,
                        targetType = row.targetType,
                        targetId = row.targetId,
                        organizationId = row.organizationId,
                        sessionId = row.sessionId,
                        reason = row.actionReason,
                        payload = buildMap {
                            put("legacy_import", "true")
                            put("original_action", row.action)
                            put("original_outcome", row.outcome)
                            put("original_event_hash", row.eventHash)
                            row.reasonCode?.let { put("reason_code", it) }
                        },
                        eventId = row.id,
                        idempotencyKey = "legacy_import:auth_audit_event:${row.id}",
                    ),
                )
                imported++
            }
            catch (e: AuditDraftInvalidException)
            {
                logger.warn("LegacyAuditImportService: draft rejected for auth_audit_event id={}: {}", row.id, e.message)
                skipped++
            }
            catch (e: AuditCaptureFailedException)
            {
                logger.error("LegacyAuditImportService: capture failed for auth_audit_event id={}: {}", row.id, e.message, e)
                failed++
            }
        }

        return LegacyImportResult(imported, skipped, failed)
    }
}
