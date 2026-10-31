package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalClaim
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

class InformationRequestDisposalServiceTest {
    private val requestRepository = mock<InformationRequestRepository>()
    private val gate = mock<InformationRequestMutationGate>()
    private val eligibility = mock<InformationRequestDisposalEligibility>()
    private val disposals = mock<RecordDisposalService>()
    private val service = InformationRequestDisposalService(
        requestRepository,
        gate,
        eligibility,
        disposals,
        mock(),
        mock(),
        Clock.systemUTC()
    )
    private val owner = RecordOwnerRef.organization(UUID.randomUUID())

    @Test
    fun `a batch claim locks every named request through one ordered gate call before it locks any claim boundary`() {
        val parent = Exchange().apply { id = UUID.randomUUID() }
        val finished = request(UUID(1, 1), InformationRequestState.CANCELLED)
        val open = request(UUID(1, 2), InformationRequestState.ISSUED)
        val named = listOf(open.id, finished.id)
        whenever(gate.lockExisting(named))
            .thenReturn(listOf(LockedInformationRequest(parent, finished), LockedInformationRequest(parent, open)))
        whenever(eligibility.assess(eq(finished), eq(RecordDisposalBasis.PRIVACY_DELETION), any(), any()))
            .thenReturn(InformationRequestDisposalAssessment.Eligible(owner, null, emptyList(), emptyList()))
        val claim = RecordDisposalClaim().apply {
            resourceType = InformationRequestDisposalService.TARGET
            resourceId = finished.id
            ownerId = requireNotNull(owner.id)
        }
        whenever(disposals.open(any())).thenReturn(RecordDisposalView(claim, emptyList(), null))

        val batch = service.claimFinished(
            named,
            RecordDisposalBasis.PRIVACY_DELETION,
            null,
            PrincipalRef.user(UUID.randomUUID())
        )

        val order = inOrder(gate, eligibility, disposals)
        order.verify(gate).lockExisting(named)
        order.verify(eligibility).lockForClaim(finished)
        order.verify(disposals).open(any())
        verify(gate, times(1)).lockExisting(any())
        verify(requestRepository, never()).findRequestByIdForUpdate(any())
        verify(eligibility, never()).lockForClaim(open)
        assertEquals(listOf(claim.id), batch.claimed.map { it.claim.id })
        assertEquals(listOf(open.id), batch.open)
    }

    private fun request(id: UUID, state: InformationRequestState): InformationRequest =
        InformationRequest().apply {
            this.id = id
            exchangeId = UUID.randomUUID()
            ownerOrganizationId = owner.id
            this.state = state
            if (state == InformationRequestState.CANCELLED) cancelledAt =
                Timestamp.from(Instant.parse("2026-09-01T08:00:00Z"))
        }
}
