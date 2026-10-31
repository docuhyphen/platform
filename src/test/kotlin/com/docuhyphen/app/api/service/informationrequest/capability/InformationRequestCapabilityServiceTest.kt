package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestCreationStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestOwnerStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestPlanQuotas
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestCallerPartyResolver
import com.docuhyphen.app.api.service.subscription.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestCapabilityServiceTest
{
    private val userId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val standingService = mock<InformationRequestExecutionStandingService>()
    private val subscriptionAccessService = mock<SubscriptionAccessService>()
    private val callerParties = mock<InformationRequestCallerPartyResolver>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val service =
        InformationRequestCapabilityService(
            standingService,
            subscriptionAccessService,
            callerParties,
            requestRepository
        )

    init
    {
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
    }

    @Test
    fun `an organization scope answers for the organization and sponsors its member's personal Templates`()
    {
        given(SubscriptionContext.forOrganization(organizationId), PlanCode.BUSINESS, null)
        given(SubscriptionContext.forUser(userId), PlanCode.FREE, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
        whenever(
            subscriptionAccessService.isFeatureAvailable(
                SubscriptionContext.forOrganization(organizationId),
                PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            ),
        ).thenReturn(true)

        val capabilities = service.forCaller(access(organizationId))

        assertEquals(SubscriptionOwnerType.ORGANIZATION, capabilities.scope.ownerType)
        assertTrue(capabilities.scope.newWorkAvailable)
        assertTrue(capabilities.typedAnswersAvailable)
        assertTrue(capabilities.personalTemplatesAvailable)
    }

    @Test
    fun `a scope that already holds requests says so whatever its plan now allows`()
    {
        given(
            SubscriptionContext.forOrganization(organizationId),
            PlanCode.BUSINESS,
            InformationRequestStandingReason.FEATURE_NOT_INCLUDED
        )
        given(SubscriptionContext.forUser(userId), PlanCode.FREE, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
        whenever(requestRepository.existsForOwner(InformationRequestOwnerType.ORGANIZATION, organizationId)).thenReturn(
            true
        )

        val capabilities = service.forCaller(access(organizationId))

        assertFalse(capabilities.scope.newWorkAvailable)
        assertTrue(capabilities.holdsRequests)
    }

    @Test
    fun `an organization without Business Fields offers requests without typed answers`()
    {
        given(SubscriptionContext.forOrganization(organizationId), PlanCode.BUSINESS, null)
        given(SubscriptionContext.forUser(userId), PlanCode.FREE, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
        whenever(
            subscriptionAccessService.isFeatureAvailable(
                SubscriptionContext.forOrganization(organizationId),
                PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            ),
        ).thenReturn(false)

        assertFalse(service.forCaller(access(organizationId)).typedAnswersAvailable)
    }

    @Test
    fun `a person on the Personal plan authors personal Templates with typed answers`()
    {
        given(SubscriptionContext.forUser(userId), PlanCode.PERSONAL, null)

        val capabilities = service.forCaller(access(null))

        assertEquals(SubscriptionOwnerType.USER, capabilities.scope.ownerType)
        assertTrue(capabilities.scope.newWorkAvailable)
        assertTrue(capabilities.typedAnswersAvailable)
        assertTrue(capabilities.personalTemplatesAvailable)
    }

    @Test
    fun `a person without the feature is told why and can still hold assigned work`()
    {
        given(SubscriptionContext.forUser(userId), PlanCode.FREE, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
        val open = InformationRequest().apply { state = InformationRequestState.IN_PROGRESS }
        whenever(callerParties.activePartiesActedFor(PrincipalRef.user(userId)))
            .thenReturn(listOf(InformationRequestParty().apply { informationRequestId = open.id }))
        whenever(requestRepository.findById(open.id)).thenReturn(open)

        val capabilities = service.forCaller(access(null))

        assertFalse(capabilities.scope.newWorkAvailable)
        assertEquals(InformationRequestStandingReason.FEATURE_NOT_INCLUDED, capabilities.scope.newWorkUnavailableReason)
        assertFalse(capabilities.personalTemplatesAvailable)
        assertTrue(capabilities.assignedWork)
    }

    private fun access(activeOrgId: UUID?) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(activeOrgId = activeOrgId))

    @Test
    fun `a scope whose open requests fill the plan allowance offers no new work and states its quotas`()
    {
        given(
            SubscriptionContext.forUser(userId),
            PlanCode.PERSONAL,
            null,
            creationReason = InformationRequestStandingReason.OPEN_REQUEST_LIMIT_REACHED
        )

        val capabilities = service.forCaller(access(null))

        assertFalse(capabilities.newWorkAvailable)
        assertEquals(InformationRequestStandingReason.OPEN_REQUEST_LIMIT_REACHED, capabilities.newWorkUnavailableReason)
        assertTrue(capabilities.scope.newWorkAvailable)
        assertTrue(capabilities.personalTemplatesAvailable)
        assertEquals(quotas, capabilities.quotas)
    }

    private val quotas = InformationRequestPlanQuotas(
        maxOpenRequests = 25,
        openRequests = 25,
        maxActingPartiesPerRequest = 10,
        maxEvidenceFilesPerRequest = 100,
        maxEvidenceBytesPerRequest = 1_024,
        maxCommittedEvidenceBytes = 4_096,
        committedEvidenceBytes = 2_048,
    )

    private fun given(
        owner: SubscriptionContext,
        plan: PlanCode,
        reason: InformationRequestStandingReason?,
        creationReason: InformationRequestStandingReason? = reason,
    ) {
        val standing = InformationRequestOwnerStanding(
            ownerType = owner.ownerType,
            ownerId = owner.ownerId,
            planCode = plan,
            status = SubscriptionStatus.ACTIVE,
            enforcementMode = SubscriptionEnforcementMode.ENFORCE,
            featureIncluded = reason != InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
            operationallySuspended = false,
            newWorkUnavailableReason = reason,
        )
        whenever(standingService.ownerStanding(owner)).thenReturn(standing)
        whenever(standingService.creationStanding(owner)).thenReturn(
            InformationRequestCreationStanding(standing, quotas, creationReason),
        )
    }
}
