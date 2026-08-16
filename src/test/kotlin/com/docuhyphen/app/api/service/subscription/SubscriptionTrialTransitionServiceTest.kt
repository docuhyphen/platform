package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class SubscriptionTrialTransitionServiceTest
{
    private val policyService: SubscriptionPolicyService = mock()
    private val service = SubscriptionTrialTransitionService(policyService, SubscriptionLifecycleValidator())
    private val ownerId = UUID.randomUUID()

    @Test
    fun `ending a user trial immediately downgrades it to active Free`()
    {
        val policy = userTrial()
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.endUserTrial(ownerId, "Trial ended by customer request")

        assertEquals(PlanCode.PERSONAL, result.before.planCode)
        assertEquals(PlanCode.FREE, result.after.planCode)
        assertEquals(SubscriptionStatus.ACTIVE, result.after.status)
        assertNull(result.after.currentPeriodStart)
        assertNull(result.after.currentPeriodEnd)
        verify(policyService).updateUserPolicy(policy)
    }

    @Test
    fun `ending an organization trial preserves Business and expires mutation access`()
    {
        val policy = organizationTrial(5)
        val previousEnd = policy.currentPeriodEnd!!.toInstant()
        whenever(policyService.findOrganizationPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.endOrganizationTrial(ownerId, "Pilot completed")

        assertEquals(PlanCode.BUSINESS, result.after.planCode)
        assertEquals(SubscriptionStatus.TRIALING, result.after.status)
        assertEquals(5, result.after.seatCapacity)
        assertTrue(result.after.currentPeriodEnd!!.isBefore(previousEnd))
        assertTrue(!result.after.currentPeriodEnd!!.isAfter(Instant.now()))
        verify(policyService).updateOrganizationPolicy(policy)
    }

    @Test
    fun `converting a user trial creates an active paid period`()
    {
        val policy = userTrial()
        val paidEnd = Instant.now().plusSeconds(2_592_000)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.convertUserTrial(
            ownerId,
            BillingFrequency.MONTHLY,
            paidEnd,
            "Customer subscribed",
        )

        assertEquals(PlanCode.PERSONAL, result.after.planCode)
        assertEquals(SubscriptionStatus.ACTIVE, result.after.status)
        assertEquals(BillingFrequency.MONTHLY, result.after.billingFrequency)
        assertEquals(paidEnd, result.after.currentPeriodEnd)
    }

    @Test
    fun `converting an organization trial applies purchased seats`()
    {
        val policy = organizationTrial(5)
        val paidEnd = Instant.now().plusSeconds(31_536_000)
        whenever(policyService.findOrganizationPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.convertOrganizationTrial(
            ownerId,
            BillingFrequency.ANNUAL,
            paidEnd,
            25,
            "Annual agreement signed",
        )

        assertEquals(SubscriptionStatus.ACTIVE, result.after.status)
        assertEquals(BillingFrequency.ANNUAL, result.after.billingFrequency)
        assertEquals(25, result.after.seatCapacity)
    }

    @Test
    fun `conversion refuses a subscription that is not trialing`()
    {
        val policy = userTrial().apply { subscriptionStatus = SubscriptionStatus.ACTIVE.name }
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val exception = assertThrows<IllegalArgumentException> {
            service.convertUserTrial(
                ownerId,
                BillingFrequency.MONTHLY,
                Instant.now().plusSeconds(3600),
                "Invalid conversion",
            )
        }

        assertEquals("Subscription is not trialing", exception.message)
    }

    @Test
    fun `conversion requires a future paid period end`()
    {
        val policy = userTrial()
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val exception = assertThrows<IllegalArgumentException> {
            service.convertUserTrial(
                ownerId,
                BillingFrequency.MONTHLY,
                Instant.now().minusSeconds(1),
                "Invalid paid period",
            )
        }

        assertEquals("Paid period end must be in the future", exception.message)
    }

    private fun userTrial(): UserSubscriptionPolicy = UserSubscriptionPolicy().apply {
        appUserId = ownerId
        planCode = PlanCode.PERSONAL.name
        subscriptionStatus = SubscriptionStatus.TRIALING.name
        currentPeriodStart = Timestamp.from(Instant.now().minusSeconds(3600))
        currentPeriodEnd = Timestamp.from(Instant.now().plusSeconds(86_400))
    }

    private fun organizationTrial(seats: Long): OrganizationSubscriptionPolicy =
        OrganizationSubscriptionPolicy().apply {
            tierCode = PlanCode.BUSINESS.name
            subscriptionStatus = SubscriptionStatus.TRIALING.name
            maxUsers = seats
            currentPeriodStart = Timestamp.from(Instant.now().minusSeconds(3600))
            currentPeriodEnd = Timestamp.from(Instant.now().plusSeconds(86_400))
        }
}
