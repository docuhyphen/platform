package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.subscription.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

/**
 * Covers the Exchange allowances applied at creation time, including the cases a customer can
 * reach by calling the API directly rather than through the app.
 */
class ExchangeInitiationSubscriptionGuardTest
{
    private val initiatorId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    private val policyService: SubscriptionPolicyService = mock()
    private val exchangeUsageCounter: ExchangeUsageCounter = mock()
    private val organizationSeatCounter: OrganizationSeatCounter = mock()

    private fun guard(
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ): ExchangeInitiationSubscriptionGuard
    {
        val accessService = SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(exchangeUsageCounter, organizationSeatCounter),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
            featureRolloutConfigService = FeatureRolloutConfigService(Optional.empty()),
        )

        return ExchangeInitiationSubscriptionGuard(accessService, policyService)
    }

    private fun givenUserPlan(
        planCode: PlanCode,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    )
    {
        val policy = UserSubscriptionPolicy().apply {
            this.appUserId = initiatorId
            this.planCode = planCode.name
            this.subscriptionStatus = status.name
        }
        whenever(policyService.findUserPolicy(initiatorId)).thenReturn(policy)
        whenever(policyService.findUserPolicyForUpdate(initiatorId)).thenReturn(policy)
    }

    private fun givenOrganizationPlan()
    {
        val policy = OrganizationSubscriptionPolicy().apply {
            this.tierCode = PlanCode.BUSINESS.name
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(policy)
        whenever(policyService.featureOverrides(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(emptyMap())
    }

    private fun givenUsage(createdThisMonth: Long, open: Long)
    {
        whenever(exchangeUsageCounter.countCreatedBetween(any(), any(), any())).thenReturn(createdThisMonth)
        whenever(exchangeUsageCounter.countOpen(any())).thenReturn(open)
    }

    private fun enforce(
        guard: ExchangeInitiationSubscriptionGuard,
        activeOrganizationId: UUID? = null,
        additionalParticipants: Int = 0,
        recipientConstraintsJson: String? = null,
    )
    {
        guard.enforceInitiation(
            initiatorId = initiatorId,
            activeOrganizationId = activeOrganizationId,
            additionalParticipants = additionalParticipants,
            recipientConstraintsJson = recipientConstraintsJson,
            at = now,
        )
    }

    @Test
    fun `a free account may create an exchange while allowance remains`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 4, open = 2)

        assertDoesNotThrow { enforce(guard()) }
    }

    @Test
    fun `a free account is refused once the monthly allowance is spent`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 5, open = 0)

        val denial = assertThrows<SubscriptionDenialException> { enforce(guard()) }.denial

        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, denial.reason)
        assertEquals(PlanFeature.EXCHANGE_CREATE, denial.feature)
        assertEquals(5L, denial.limit)
        assertEquals(5L, denial.currentValue)
        assertEquals(PlanCode.PERSONAL, denial.upgradePlanCode)
    }

    @Test
    fun `open exchanges block creation even when the monthly allowance remains`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 1, open = 3)

        val denial = assertThrows<SubscriptionDenialException> { enforce(guard()) }.denial

        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, denial.reason)
        assertEquals(3L, denial.limit)
    }

    @Test
    fun `freeing an open exchange restores creation capacity`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 1, open = 2)

        assertDoesNotThrow { enforce(guard()) }
    }

    @Test
    fun `a free account may not add a participant beyond the primary recipient`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 0, open = 0)

        val denial = assertThrows<SubscriptionDenialException> {
            enforce(guard(), additionalParticipants = 1)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.MULTIPLE_PARTICIPANTS, denial.feature)
    }

    @Test
    fun `a free account may not add advanced constraints during initiation`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 0, open = 0)

        val denial = assertThrows<SubscriptionDenialException> {
            enforce(guard(), recipientConstraintsJson = "{\"noReshare\":true}")
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.ADVANCED_ACCESS_CONTROLS, denial.feature)
    }

    @Test
    fun `a suspended account may not create an exchange`()
    {
        givenUserPlan(PlanCode.PERSONAL, status = SubscriptionStatus.SUSPENDED)

        val denial = assertThrows<SubscriptionDenialException> { enforce(guard()) }.denial

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, denial.reason)
    }

    @Test
    fun `a personal account has no exchange allowance to spend`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        assertDoesNotThrow { enforce(guard(), additionalParticipants = 4) }
        verifyNoInteractions(exchangeUsageCounter)
    }

    @Test
    fun `an organization context is never charged against the personal plan of the member`()
    {
        givenUserPlan(PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow {
            enforce(guard(), activeOrganizationId = organizationId, additionalParticipants = 6)
        }

        verify(policyService, never()).findUserPolicy(initiatorId)
        verify(policyService, never()).findUserPolicyForUpdate(initiatorId)
    }

    @Test
    fun `a capped subject is locked before its usage is counted`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 0, open = 0)

        enforce(guard())

        verify(policyService).findUserPolicyForUpdate(initiatorId)
    }

    @Test
    fun `an uncapped subject is not locked`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        enforce(guard())

        verify(policyService, never()).findUserPolicyForUpdate(initiatorId)
    }

    @Test
    fun `report only mode records the refusal and lets the exchange through`()
    {
        givenUserPlan(PlanCode.FREE)
        givenUsage(createdThisMonth = 9, open = 9)

        assertDoesNotThrow {
            enforce(guard(SubscriptionEnforcementMode.REPORT_ONLY), additionalParticipants = 2)
        }
    }

    @Test
    fun `disabled enforcement performs no plan work at all`()
    {
        enforce(guard(SubscriptionEnforcementMode.OFF), additionalParticipants = 2)

        verifyNoInteractions(policyService)
        verifyNoInteractions(exchangeUsageCounter)
    }
}


