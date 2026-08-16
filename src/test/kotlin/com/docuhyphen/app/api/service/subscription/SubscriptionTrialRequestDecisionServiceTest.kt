package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionTrialGrant
import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequest
import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequestStatus
import com.docuhyphen.app.api.repository.subscription.SubscriptionTrialRequestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SubscriptionTrialRequestDecisionServiceTest
{
    private val repository: SubscriptionTrialRequestRepository = mock()
    private val eligibilityService: SubscriptionTrialRequestEligibilityService = mock()
    private val trialService: SubscriptionTrialService = mock()
    private val requestService: SubscriptionTrialRequestService = mock()
    private val service = SubscriptionTrialRequestDecisionService(
        repository,
        eligibilityService,
        trialService,
        requestService,
    )
    private val ownerId = UUID.randomUUID()
    private val reviewerId = UUID.randomUUID()

    @Test
    fun `approval starts the trial and links its grant`()
    {
        val request = pendingRequest()
        val grant = SubscriptionTrialGrant()
        val state = SubscriptionTrialState(
            PlanCode.PERSONAL,
            SubscriptionStatus.TRIALING,
            null,
            Instant.now(),
            Instant.now().plusSeconds(86_400),
            null,
        )
        val mutation = SubscriptionTrialMutation(
            SubscriptionOwnerType.USER,
            ownerId,
            state.copy(planCode = PlanCode.FREE, status = SubscriptionStatus.ACTIVE),
            state,
            grant,
        )
        whenever(repository.findByIdForDecision(request.id)).thenReturn(request)
        whenever(eligibilityService.evaluateForRequest(SubscriptionOwnerType.USER, ownerId))
            .thenReturn(SubscriptionTrialRequestEligibility(true))
        whenever(trialService.startUserTrial(ownerId, 14, "Approved pilot", reviewerId)).thenReturn(mutation)
        val view = SubscriptionTrialRequestView(request, "Requester", "Requester", "requester@example.test")
        whenever(requestService.view(request)).thenReturn(view)

        val result = service.decide(
            request.id,
            SubscriptionTrialRequestStatus.APPROVED,
            14,
            null,
            "Approved pilot",
            reviewerId,
        )

        assertEquals(SubscriptionTrialRequestStatus.APPROVED.name, result.request.status)
        assertEquals(grant.id, result.request.trialGrantId)
        verify(requestService).notifyDecision(view)
    }

    @Test
    fun `rejection records a reason without starting a trial`()
    {
        val request = pendingRequest()
        whenever(repository.findByIdForDecision(request.id)).thenReturn(request)
        val view = SubscriptionTrialRequestView(request, "Requester", "Requester", "requester@example.test")
        whenever(requestService.view(request)).thenReturn(view)

        service.decide(
            request.id,
            SubscriptionTrialRequestStatus.REJECTED,
            null,
            null,
            "Not eligible for this pilot",
            reviewerId,
        )

        assertEquals(SubscriptionTrialRequestStatus.REJECTED.name, request.status)
        assertNull(request.trialGrantId)
        verify(trialService, never()).startUserTrial(any(), any(), any(), any())
        verify(requestService).notifyDecision(view)
    }

    private fun pendingRequest() = SubscriptionTrialRequest().apply {
        ownerType = SubscriptionOwnerType.USER.name
        ownerId = this@SubscriptionTrialRequestDecisionServiceTest.ownerId
        requestedByAppUserId = ownerId
        planCode = PlanCode.PERSONAL.name
        status = SubscriptionTrialRequestStatus.PENDING.name
    }
}
