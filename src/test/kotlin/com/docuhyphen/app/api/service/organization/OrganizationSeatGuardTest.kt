package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.service.subscription.BillingFrequency
import com.docuhyphen.app.api.service.subscription.EffectiveSubscription
import com.docuhyphen.app.api.service.subscription.PlanCatalog
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationSeatGuardTest
{
    private val organizationId = UUID.randomUUID()
    private val accessService = mock<SubscriptionAccessService>()
    private val policyService = mock<SubscriptionPolicyService>()
    private val guard = OrganizationSeatGuard(accessService, policyService)

    @Test
    fun `disabled enforcement does not resolve or lock a subscription`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.OFF)

        guard.enforceAvailableSeat(organizationId)

        verify(accessService, never()).resolve(org.mockito.kotlin.any())
        verify(policyService, never()).findOrganizationPolicyForUpdate(organizationId)
    }

    @Test
    fun `uncapped organization does not take a subscription lock`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(accessService.resolve(org.mockito.kotlin.any())).thenReturn(subscription(null))

        guard.enforceAvailableSeat(organizationId)

        verify(policyService, never()).findOrganizationPolicyForUpdate(organizationId)
        verify(accessService).requireMutationAllowed(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(accessService, never()).requireOrganizationSeat(organizationId)
    }

    @Test
    fun `capped organization locks its policy before counting the available seat`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(accessService.resolve(org.mockito.kotlin.any())).thenReturn(subscription(5))
        whenever(policyService.findOrganizationPolicyForUpdate(organizationId))
            .thenReturn(OrganizationSubscriptionPolicy())

        guard.enforceAvailableSeat(organizationId)

        inOrder(policyService, accessService) {
            verify(policyService).findOrganizationPolicyForUpdate(organizationId)
            verify(accessService).requireMutationAllowed(org.mockito.kotlin.any(), org.mockito.kotlin.any())
            verify(accessService).requireOrganizationSeat(organizationId)
        }
    }

    private fun subscription(purchasedSeats: Long?): EffectiveSubscription = EffectiveSubscription(
        planCode = PlanCode.BUSINESS,
        ownerType = SubscriptionOwnerType.ORGANIZATION,
        ownerId = organizationId,
        status = SubscriptionStatus.ACTIVE,
        features = PlanCatalog.definitionOf(PlanCode.BUSINESS).features,
        limits = PlanCatalog.definitionOf(PlanCode.BUSINESS).limits,
        billingFrequency = BillingFrequency.MONTHLY,
        currentPeriodStart = null,
        currentPeriodEnd = null,
        gracePeriodEnd = null,
        purchasedSeats = purchasedSeats,
        upgradePlanCode = null,
    )
}
