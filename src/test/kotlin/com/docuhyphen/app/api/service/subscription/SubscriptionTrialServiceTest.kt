package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.SubscriptionTrialGrant
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.repository.SubscriptionTrialGrantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class SubscriptionTrialServiceTest
{
    private val policyService: SubscriptionPolicyService = mock()
    private val grantRepository: SubscriptionTrialGrantRepository = mock()
    private val service = SubscriptionTrialService(
        policyMutationService = SubscriptionTrialPolicyMutationService(
            subscriptionPolicyService = policyService,
            lifecycleValidator = SubscriptionLifecycleValidator(),
        ),
        trialGrantRepository = grantRepository,
    )
    private val ownerId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()

    init
    {
        whenever(grantRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SubscriptionTrialGrant>(0)
        }
    }

    @Test
    fun `starting a user trial applies Personal lifecycle and records the grant`()
    {
        val policy = userPolicy(PlanCode.FREE, SubscriptionStatus.ACTIVE)
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.startUserTrial(ownerId, 14, "Sales demonstration", actorId)

        assertEquals(PlanCode.PERSONAL, result.after.planCode)
        assertEquals(SubscriptionStatus.TRIALING, result.after.status)
        assertTrue(result.after.currentPeriodEnd!!.isAfter(result.after.currentPeriodStart))
        assertEquals(SubscriptionTrialSource.PLATFORM_ADMIN.name, result.grant.source)
        assertEquals(actorId, result.grant.grantedByAppUserId)
        verify(policyService).updateUserPolicy(policy)
    }

    @Test
    fun `starting an organization trial applies Business seats and default lifecycle`()
    {
        val policy = organizationPolicy(SubscriptionStatus.ACTIVE, null)
        whenever(policyService.ensureOrganizationPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findOrganizationPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.startOrganizationTrial(ownerId, 30, 5, "Implementation pilot", actorId)

        assertEquals(PlanCode.BUSINESS, result.after.planCode)
        assertEquals(SubscriptionStatus.TRIALING, result.after.status)
        assertEquals(5, result.after.seatCapacity)
        verify(policyService).updateOrganizationPolicy(policy)
    }

    @Test
    fun `an active trial cannot be started again`()
    {
        val policy = userPolicy(PlanCode.PERSONAL, SubscriptionStatus.TRIALING).apply {
            currentPeriodStart = Timestamp.from(Instant.now().minusSeconds(60))
            currentPeriodEnd = Timestamp.from(Instant.now().plusSeconds(3600))
        }
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val exception = assertThrows<IllegalArgumentException> {
            service.startUserTrial(ownerId, 14, "Duplicate grant", actorId)
        }

        assertEquals("An active trial already exists", exception.message)
        verify(policyService, never()).updateUserPolicy(policy)
    }

    @Test
    fun `a paid user subscription cannot be replaced by a trial`()
    {
        val policy = userPolicy(PlanCode.PERSONAL, SubscriptionStatus.ACTIVE).apply {
            billingFrequency = BillingFrequency.MONTHLY.name
            externalBillingSubscriptionRef = "subscription-123"
        }
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val exception = assertThrows<IllegalArgumentException> {
            service.startUserTrial(ownerId, 14, "Replace paid lifecycle", actorId)
        }

        assertEquals("This account already has paid billing set up", exception.message)
        verify(policyService, never()).updateUserPolicy(policy)
    }

    @Test
    fun `an organization with an external billing reference cannot be replaced by a trial`()
    {
        val policy = organizationPolicy(SubscriptionStatus.ACTIVE, 25).apply {
            externalBillingCustomerRef = "customer-123"
        }
        whenever(policyService.ensureOrganizationPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findOrganizationPolicyForUpdate(ownerId)).thenReturn(policy)

        val exception = assertThrows<IllegalArgumentException> {
            service.startOrganizationTrial(ownerId, 30, 5, "Replace paid lifecycle", actorId)
        }

        assertEquals("This account already has paid billing set up", exception.message)
        assertEquals(25L, policy.maxUsers)
        verify(policyService, never()).updateOrganizationPolicy(policy)
    }

    @Test
    fun `extending a trial records only the added interval`()
    {
        val previousEnd = Instant.now().plusSeconds(3600)
        val newEnd = previousEnd.plusSeconds(7200)
        val policy = userPolicy(PlanCode.PERSONAL, SubscriptionStatus.TRIALING).apply {
            currentPeriodStart = Timestamp.from(Instant.now().minusSeconds(3600))
            currentPeriodEnd = Timestamp.from(previousEnd)
        }
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)

        val result = service.extendUserTrial(ownerId, newEnd, "Pilot extension", actorId)

        assertEquals(newEnd, result.after.currentPeriodEnd)
        assertEquals(previousEnd, result.grant.startedAt.toInstant())
        assertEquals(newEnd, result.grant.endedAt.toInstant())
    }

    @Test
    fun `automatic trial eligibility uses durable grant history`()
    {
        whenever(grantRepository.hasAutomaticGrant(SubscriptionOwnerType.USER, ownerId)).thenReturn(true)

        assertTrue(service.hasConsumedAutomaticTrial(SubscriptionOwnerType.USER, ownerId))
    }

    @Test
    fun `grant history captures the normalized audit reason`()
    {
        val policy = userPolicy(PlanCode.FREE, SubscriptionStatus.ACTIVE)
        whenever(policyService.ensureUserPolicy(ownerId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(ownerId)).thenReturn(policy)
        val grantCaptor = argumentCaptor<SubscriptionTrialGrant>()

        service.startUserTrial(ownerId, 14, "  Billing recovery  ", actorId)

        verify(grantRepository).save(grantCaptor.capture())
        assertEquals("Billing recovery", grantCaptor.firstValue.reason)
    }

    private fun userPolicy(planCode: PlanCode, status: SubscriptionStatus): UserSubscriptionPolicy
    {
        return UserSubscriptionPolicy().apply {
            appUserId = ownerId
            this.planCode = planCode.name
            subscriptionStatus = status.name
        }
    }

    private fun organizationPolicy(
        status: SubscriptionStatus,
        seatCapacity: Long?,
    ): OrganizationSubscriptionPolicy
    {
        return OrganizationSubscriptionPolicy().apply {
            tierCode = PlanCode.BUSINESS.name
            subscriptionStatus = status.name
            maxUsers = seatCapacity
        }
    }
}
