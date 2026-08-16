package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class SubscriptionTrialRequestEligibilityServiceTest
{
    private val policyService: SubscriptionPolicyService = mock()
    private val service = SubscriptionTrialRequestEligibilityService(policyService)
    private val ownerId = UUID.randomUUID()

    @Test
    fun `Free user can request a Personal trial`()
    {
        val policy = UserSubscriptionPolicy().apply {
            appUserId = ownerId
            planCode = PlanCode.FREE.name
            subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        assertTrue(service.evaluateForRequest(SubscriptionOwnerType.USER, ownerId).eligible)
    }

    @Test
    fun `active trial cannot be requested again`()
    {
        val policy = UserSubscriptionPolicy().apply {
            appUserId = ownerId
            planCode = PlanCode.PERSONAL.name
            subscriptionStatus = SubscriptionStatus.TRIALING.name
            currentPeriodEnd = Timestamp.from(Instant.now().plusSeconds(3600))
        }
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        assertFalse(service.evaluateForRequest(SubscriptionOwnerType.USER, ownerId).eligible)
    }

    @Test
    fun `paid billing lifecycle cannot request a trial`()
    {
        val policy = UserSubscriptionPolicy().apply {
            appUserId = ownerId
            planCode = PlanCode.PERSONAL.name
            subscriptionStatus = SubscriptionStatus.ACTIVE.name
            billingFrequency = BillingFrequency.MONTHLY.name
        }
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val eligibility = service.evaluateForRequest(SubscriptionOwnerType.USER, ownerId)
        assertFalse(eligibility.eligible)
        assertEquals("This account already has paid billing set up", eligibility.reason)
    }

    @Test
    fun `unbilled organization can request a Business trial`()
    {
        val policy = OrganizationSubscriptionPolicy().apply {
            tierCode = PlanCode.BUSINESS.name
            subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        whenever(policyService.ensureOrganizationPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findOrganizationPolicyForUpdate(ownerId)).thenReturn(policy)

        assertTrue(service.evaluateForRequest(SubscriptionOwnerType.ORGANIZATION, ownerId).eligible)
    }
}
