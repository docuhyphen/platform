package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.recordpreservation.ChangeRecordPreservationHoldScopeCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldEventRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordRetentionScheduleRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class RecordPreservationAuditBoundaryTest {
    private val recorder = mock<AuditRecorder>()
    private val audit = RecordPreservationAudit(recorder)
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val owner = RecordOwnerRef.user(principal.id)
    private val now = Instant.parse("2026-10-27T08:00:00Z")

    @Test
    fun `hold audit preserves the placement reason and effective instant`() {
        val hold = hold()
        audit.hold(AuditEventType.AUDIT_LEGAL_HOLD_PLACED, hold, principal)
        val draft = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(draft.capture())
        assertAll(
            { assertEquals("Preserve the submitted records", draft.firstValue.reason) },
            { assertEquals(now.toString(), draft.firstValue.payload["effectiveFrom"]) },
            { assertNotNull(draft.firstValue.actorLabel) },
        )
    }

    @Test
    fun `a changed hold scope audits the reason for that change`() {
        val holds = mock<RecordPreservationHoldRepository>()
        val events = mock<RecordPreservationHoldEventRepository>()
        val hold = hold()
        whenever(holds.findForUpdate(hold.id)).thenReturn(hold)
        whenever(holds.update(any())).thenAnswer { it.getArgument(0) }
        whenever(events.findForHold(hold.id)).thenReturn(emptyList())
        val service = RecordPreservationHoldService(holds, events, mock(), audit, Clock.fixed(now, ZoneOffset.UTC))
        service.changeScope(
            ChangeRecordPreservationHoldScopeCommand(
                hold.id, owner,
                RecordPreservationScope.DESCENDANTS_AND_REFERENCES, "Include the related records", principal
            )
        )
        val draft = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(draft.capture())
        assertEquals("Include the related records", draft.firstValue.reason)
    }

    @Test
    fun `system disposal audit carries an explicit system identity and claim correlation`() {
        val system = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
        val claim = UUID.randomUUID().toString()
        audit.disposal(
            AuditEventType.RECORD_DISPOSAL_FINALIZED, owner, "INFORMATION_REQUEST",
            UUID.randomUUID().toString(), system, mapOf("claimId" to claim), "finalized-$claim"
        )
        val draft = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(draft.capture())
        assertAll(
            { assertEquals("service-account:${system.id}", draft.firstValue.actorLabel) },
            { assertEquals(claim, draft.firstValue.businessTransactionId) },
            { assertNull(draft.firstValue.sessionId) },
        )
    }

    @Test
    fun `retention day values are bounded before a schedule is stored`() {
        val schedules = mock<RecordRetentionScheduleRepository>()
        whenever(schedules.findVersions(any(), any())).thenReturn(emptyList())
        whenever(schedules.save(any())).thenAnswer { it.getArgument<RecordRetentionSchedule>(0) }
        val service = RecordRetentionScheduleService(schedules, mock(), Clock.fixed(now, ZoneOffset.UTC))
        assertThrows(RecordPreservationRequestException::class.java) {
            service.publish(
                PublishRecordRetentionScheduleCommand(
                    owner,
                    "INFORMATION_REQUEST",
                    0,
                    Int.MAX_VALUE,
                    principal
                )
            )
        }
        assertThrows(RecordPreservationRequestException::class.java) {
            service.publish(
                PublishRecordRetentionScheduleCommand(
                    owner,
                    "INFORMATION_REQUEST",
                    Int.MAX_VALUE,
                    null,
                    principal
                )
            )
        }
        verify(schedules, never()).save(any())
    }

    private fun hold() = RecordPreservationHold().apply {
        ownerKind = RecordOwnerKind.USER
        ownerId = principal.id
        resourceType = "INFORMATION_REQUEST"
        resourceId = UUID.randomUUID().toString()
        reason = "Preserve the submitted records"
        effectiveFrom = Timestamp.from(now)
        scope = RecordPreservationScope.RESOURCE
        placedByPrincipalId = principal.id
    }
}