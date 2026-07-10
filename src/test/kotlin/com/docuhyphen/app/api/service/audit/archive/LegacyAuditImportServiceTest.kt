package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.entity.AuthAuditEvent
import com.docuhyphen.app.api.repository.AuthAuditEventRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Phase 4 task 5 gate for [LegacyAuditImportService]:
 *  - each legacy `auth_audit_event` row not yet in the ledger is captured exactly once as
 *    [com.docuhyphen.app.api.service.audit.catalog.AuditEventType.LEGACY_AUDIT_EVENT_IMPORTED],
 *    carrying `legacy_import=true` and the original action/outcome in its payload.
 *  - a rejected draft (invalid) and a failed capture are each counted, not thrown, so one bad row
 *    does not abort the rest of the batch.
 */
class LegacyAuditImportServiceTest
{
    private fun legacyRow(): AuthAuditEvent = AuthAuditEvent().apply {
        id = UUID.randomUUID()
        actorId = UUID.randomUUID()
        actorRole = "OWNER"
        targetType = "EXCHANGE"
        targetId = UUID.randomUUID().toString()
        action = "SIGN_IN_COMPLETION"
        outcome = "SUCCESS"
        eventHash = "legacy-hash"
    }

    @Test
    fun `imports a legacy row exactly once with legacy_import marked in the payload`()
    {
        val row = legacyRow()
        val authAuditEventRepository = mock<AuthAuditEventRepository>()
        whenever(authAuditEventRepository.findLegacyUnimported(any())).thenReturn(listOf(row))

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), row.id))

        val service = LegacyAuditImportService(authAuditEventRepository, auditRecorder)
        val result = service.importBatch()

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        val draft = captor.firstValue

        assertEquals(row.id, draft.eventId)
        assertEquals("true", draft.payload["legacy_import"])
        assertEquals("SIGN_IN_COMPLETION", draft.payload["original_action"])
        assertEquals("legacy_import:auth_audit_event:${row.id}", draft.idempotencyKey)
    }

    @Test
    fun `a draft rejected as invalid is counted as skipped, not thrown`()
    {
        val row = legacyRow()
        val authAuditEventRepository = mock<AuthAuditEventRepository>()
        whenever(authAuditEventRepository.findLegacyUnimported(any())).thenReturn(listOf(row))

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenThrow(AuditDraftInvalidException(listOf("boom")))

        val service = LegacyAuditImportService(authAuditEventRepository, auditRecorder)
        val result = service.importBatch()

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `a fail-closed capture failure is counted as failed, not thrown, so the rest of the batch still runs`()
    {
        val badRow = legacyRow()
        val goodRow = legacyRow()
        val authAuditEventRepository = mock<AuthAuditEventRepository>()
        whenever(authAuditEventRepository.findLegacyUnimported(any())).thenReturn(listOf(badRow, goodRow))

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenAnswer { invocation ->
            val draft = invocation.getArgument<AuditEventDraft>(0)
            if (draft.eventId == badRow.id) throw AuditCaptureFailedException("db down", null)
            AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
        }

        val service = LegacyAuditImportService(authAuditEventRepository, auditRecorder)
        val result = service.importBatch()

        assertEquals(1, result.imported)
        assertEquals(1, result.failed)
    }
}
