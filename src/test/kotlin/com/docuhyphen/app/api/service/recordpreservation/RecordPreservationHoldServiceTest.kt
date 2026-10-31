package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.recordpreservation.ChangeRecordPreservationHoldScopeCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.recordpreservation.RecordDisposalClaimRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldEventRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class RecordPreservationHoldServiceTest {
    private val holdRepository = mock<RecordPreservationHoldRepository>()
    private val eventRepository = mock<RecordPreservationHoldEventRepository>()
    private val claimRepository = mock<RecordDisposalClaimRepository>()
    private val audit = mock<RecordPreservationAudit>()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val holds = RecordPreservationHoldService(
        holdRepository,
        eventRepository,
        claimRepository,
        audit,
        Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC),
    )

    @ParameterizedTest
    @MethodSource("owners")
    fun `an active hold changes its scope with a recorded reason, a new revision, and an audit event`(owner: RecordOwnerRef) {
        val hold = activeHold(owner, RecordPreservationScope.RESOURCE)
        whenever(holdRepository.findForUpdate(hold.id)).thenReturn(hold)
        whenever(holdRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(eventRepository.nextEventNumber(hold.id)).thenReturn(2)
        whenever(eventRepository.findForHold(hold.id)).thenReturn(emptyList())

        val changed = holds.changeScope(
            ChangeRecordPreservationHoldScopeCommand(
                hold.id,
                owner,
                RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                "  widened to related records ",
                principal
            ),
        )

        assertEquals(RecordPreservationScope.DESCENDANTS_AND_REFERENCES, changed.hold.scope)
        assertEquals(2, changed.hold.holdRevision)
        assertEquals(Instant.parse("2026-10-01T08:00:00Z"), changed.hold.updatedAt.toInstant())
        val event = argumentCaptor<RecordPreservationHoldEvent>()
        verify(eventRepository).save(event.capture())
        assertEquals(RecordPreservationHoldEventKind.SCOPE_CHANGED, event.firstValue.eventKind)
        assertEquals(RecordPreservationScope.DESCENDANTS_AND_REFERENCES, event.firstValue.scope)
        assertEquals("widened to related records", event.firstValue.reason)
        assertEquals(2, event.firstValue.eventNumber)
        assertEquals(principal.id, event.firstValue.principalId)
        verify(audit).hold(
            eq(AuditEventType.AUDIT_LEGAL_HOLD_SCOPE_CHANGED),
            eq(hold),
            eq(principal),
            eq("widened to related records")
        )
    }

    @Test
    fun `a scope change to the scope the hold already has is refused`() {
        val owner = RecordOwnerRef.organization(UUID.randomUUID())
        val hold = activeHold(owner, RecordPreservationScope.RESOURCE)
        whenever(holdRepository.findForUpdate(hold.id)).thenReturn(hold)

        val refusal = assertThrows(RecordPreservationException::class.java) {
            holds.changeScope(
                ChangeRecordPreservationHoldScopeCommand(
                    hold.id,
                    owner,
                    RecordPreservationScope.RESOURCE,
                    "unchanged",
                    principal
                )
            )
        }

        assertEquals(RecordPreservationErrorCatalog.HOLD_SCOPE_UNCHANGED, refusal.reasonCode)
        assertUnchanged(hold)
    }

    @Test
    fun `a released hold does not change its scope`() {
        val owner = RecordOwnerRef.user(principal.id)
        val hold =
            activeHold(owner, RecordPreservationScope.RESOURCE).apply { status = RecordPreservationHoldStatus.RELEASED }
        whenever(holdRepository.findForUpdate(hold.id)).thenReturn(hold)

        val refusal = assertThrows(RecordPreservationException::class.java) {
            holds.changeScope(
                ChangeRecordPreservationHoldScopeCommand(
                    hold.id,
                    owner,
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "widened",
                    principal
                ),
            )
        }

        assertEquals(RecordPreservationErrorCatalog.HOLD_RELEASED, refusal.reasonCode)
        assertUnchanged(hold)
    }

    @Test
    fun `a hold whose new scope covers a record under disposal is refused`() {
        val owner = RecordOwnerRef.PLATFORM
        val hold = activeHold(owner, RecordPreservationScope.RESOURCE)
        whenever(holdRepository.findForUpdate(hold.id)).thenReturn(hold)
        whenever(claimRepository.hasOpenClaimCovering(any())).thenReturn(true)

        val refusal = assertThrows(RecordPreservationException::class.java) {
            holds.changeScope(
                ChangeRecordPreservationHoldScopeCommand(
                    hold.id,
                    owner,
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "widened",
                    principal
                ),
            )
        }

        assertEquals(RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS, refusal.reasonCode)
        verify(holdRepository, never()).update(any())
        verify(eventRepository, never()).save(any())
    }

    @Test
    fun `a scope change states its reason and names a hold of its own owner`() {
        val owner = RecordOwnerRef.organization(UUID.randomUUID())
        val hold = activeHold(owner, RecordPreservationScope.RESOURCE)
        whenever(holdRepository.findForUpdate(hold.id)).thenReturn(hold)

        assertThrows(RecordPreservationRequestException::class.java) {
            holds.changeScope(
                ChangeRecordPreservationHoldScopeCommand(
                    hold.id,
                    owner,
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "   ",
                    principal
                )
            )
        }
        assertThrows(RecordPreservationNotFoundException::class.java) {
            holds.changeScope(
                ChangeRecordPreservationHoldScopeCommand(
                    hold.id,
                    RecordOwnerRef.organization(UUID.randomUUID()),
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "widened",
                    principal,
                ),
            )
        }
        assertUnchanged(hold)
    }

    @Test
    fun `hold lookups use the same canonical keys as placement`() {
        val owner = RecordOwnerRef.user(principal.id)
        val id = UUID.randomUUID()
        whenever(holdRepository.findActiveCovering(any(), any())).thenReturn(emptyList())
        holds.coveringHolds(
            owner,
            listOf(
                com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey(
                    "exchange",
                    id.toString().uppercase(),
                    true
                )
            )
        )
        verify(holdRepository).findActiveCovering(
            owner,
            listOf(
                com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey(
                    "EXCHANGE",
                    id.toString(),
                    true
                )
            )
        )
    }

    private fun assertUnchanged(hold: RecordPreservationHold) {
        assertEquals(1, hold.holdRevision)
        verify(holdRepository, never()).update(any())
        verify(eventRepository, never()).save(any())
        verify(audit, never()).hold(any(), any(), any(), any())
    }

    private fun activeHold(owner: RecordOwnerRef, scope: RecordPreservationScope) = RecordPreservationHold().apply {
        ownerKind = owner.kind
        ownerId = owner.id
        resourceType = "EXCHANGE"
        resourceId = UUID.randomUUID().toString()
        reason = "preserve records"
        this.scope = scope
        placedByPrincipalId = principal.id
    }

    companion object {
        @JvmStatic
        fun owners() = listOf(
            RecordOwnerRef.PLATFORM,
            RecordOwnerRef.organization(UUID.randomUUID()),
            RecordOwnerRef.user(UUID.randomUUID()),
        )
    }
}
