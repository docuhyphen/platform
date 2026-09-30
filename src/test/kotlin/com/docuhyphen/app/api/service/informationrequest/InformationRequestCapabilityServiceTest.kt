package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestStandingReason
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestCapabilityServiceTest
{
    private val userId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val standingService = mock<InformationRequestExecutionStandingService>()
    private val subscriptionAccessService = mock<SubscriptionAccessService>()
    private val shareService = mock<ShareService>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val service = InformationRequestCapabilityService(standingService, subscriptionAccessService, shareService, requestRepository)

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
        given(SubscriptionContext.forOrganization(organizationId), PlanCode.BUSINESS, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
        given(SubscriptionContext.forUser(userId), PlanCode.FREE, InformationRequestStandingReason.FEATURE_NOT_INCLUDED)
        whenever(requestRepository.existsForOwner(InformationRequestOwnerType.ORGANIZATION, organizationId)).thenReturn(true)

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
        whenever(shareService.holdsActiveShareOn(ResourceType.INFORMATION_REQUEST, PrincipalRef.user(userId))).thenReturn(true)

        val capabilities = service.forCaller(access(null))

        assertFalse(capabilities.scope.newWorkAvailable)
        assertEquals(InformationRequestStandingReason.FEATURE_NOT_INCLUDED, capabilities.scope.newWorkUnavailableReason)
        assertFalse(capabilities.personalTemplatesAvailable)
        assertTrue(capabilities.assignedWork)
    }

    private fun access(activeOrgId: UUID?) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(activeOrgId = activeOrgId))

    private fun given(owner: SubscriptionContext, plan: PlanCode, reason: InformationRequestStandingReason?)
    {
        whenever(standingService.ownerStanding(owner)).thenReturn(
            InformationRequestOwnerStanding(
                ownerType = owner.ownerType,
                ownerId = owner.ownerId,
                planCode = plan,
                status = SubscriptionStatus.ACTIVE,
                enforcementMode = SubscriptionEnforcementMode.ENFORCE,
                featureIncluded = reason != InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
                operationallySuspended = false,
                newWorkUnavailableReason = reason,
            ),
        )
    }
}
