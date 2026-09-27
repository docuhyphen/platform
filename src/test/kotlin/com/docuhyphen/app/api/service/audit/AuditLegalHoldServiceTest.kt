package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEvent
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldEventKind
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.recordpreservation.RecordDisposalClaimRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldEventRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationHoldRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAudit
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AuditLegalHoldServiceTest
{
    private val holdRepository = mock<RecordPreservationHoldRepository>()
    private val eventRepository = mock<RecordPreservationHoldEventRepository>()
    private val claimRepository = mock<RecordDisposalClaimRepository>()
    private val audit = mock<RecordPreservationAudit>()
    private val holds = RecordPreservationHoldService(
        holdRepository, eventRepository, claimRepository, audit, Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC),
    )
    private val service = AuditLegalHoldService(holds, mock(), mock())

    @Test
    fun `placing a hold through audit governance records a neutral hold with its placement history`()
    {
        whenever(holdRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(eventRepository.nextEventNumber(any())).thenReturn(1)
        val organizationId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val hold = service.placeHold(organizationId, "exchange", "abc", "records review hold", null, userId)

        assertEquals(RecordPreservationHoldStatus.ACTIVE, hold.status)
        assertEquals(RecordOwnerKind.ORGANIZATION, hold.ownerKind)
        assertEquals(organizationId, hold.ownerId)
        assertEquals("EXCHANGE", hold.resourceType)
        assertEquals(RecordPreservationScope.RESOURCE, hold.scope)
        assertEquals(userId, hold.placedByPrincipalId)
        val event = argumentCaptor<RecordPreservationHoldEvent>()
        verify(eventRepository).save(event.capture())
        assertEquals(RecordPreservationHoldEventKind.PLACED, event.firstValue.eventKind)
        verify(audit).hold(eq(AuditEventType.AUDIT_LEGAL_HOLD_PLACED), any(), any())
    }

    @Test
    fun `a hold is visible to both subsystems and releasing it clears the preservation`()
    {
        val organizationId = UUID.randomUUID()
        val hold = activeHold(RecordOwnerRef.organization(organizationId))
        whenever(holdRepository.findActiveCovering(eq(RecordOwnerRef.organization(organizationId)), any())).thenReturn(listOf(hold))
        assertTrue(service.isUnderHold(organizationId, "EXCHANGE", "abc"))

        whenever(holdRepository.findForUpdate(hold.id)).thenReturn(hold)
        whenever(holdRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(eventRepository.nextEventNumber(hold.id)).thenReturn(2)
        val released = service.releaseHold(hold.id, organizationId, UUID.randomUUID())

        assertEquals(RecordPreservationHoldStatus.RELEASED, released.status)
        assertEquals(2, released.holdRevision)
        whenever(holdRepository.findActiveCovering(any(), any())).thenReturn(emptyList())
        assertFalse(service.isUnderHold(organizationId, "EXCHANGE", "abc"))
    }

    @Test
    fun `a released hold cannot be released again and another owner's hold is not found`()
    {
        val released = activeHold(RecordOwnerRef.PLATFORM).apply { status = RecordPreservationHoldStatus.RELEASED }
        whenever(holdRepository.findForUpdate(released.id)).thenReturn(released)
        assertThrows(IllegalArgumentException::class.java) { service.releaseHold(released.id, null, UUID.randomUUID()) }

        val foreign = activeHold(RecordOwnerRef.organization(UUID.randomUUID()))
        whenever(holdRepository.findForUpdate(foreign.id)).thenReturn(foreign)
        assertThrows(AuditLegalHoldNotFoundException::class.java) {
            service.releaseHold(foreign.id, UUID.randomUUID(), UUID.randomUUID())
        }
        assertEquals(RecordPreservationHoldStatus.ACTIVE, foreign.status)
        verify(holdRepository, never()).update(any())
    }

    @Test
    fun `a hold cannot be placed on a record already claimed for disposal`()
    {
        whenever(claimRepository.hasOpenClaimCovering(any())).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.placeHold(UUID.randomUUID(), "INFORMATION_REQUEST", UUID.randomUUID().toString(), "preserve", null, UUID.randomUUID())
        }
        verify(holdRepository, never()).save(any())
    }

    private fun activeHold(owner: RecordOwnerRef) = RecordPreservationHold().apply {
        ownerKind = owner.kind
        ownerId = owner.id
        resourceType = "EXCHANGE"
        resourceId = "abc"
        reason = "records review hold"
        placedByPrincipalId = UUID.randomUUID()
    }
}
