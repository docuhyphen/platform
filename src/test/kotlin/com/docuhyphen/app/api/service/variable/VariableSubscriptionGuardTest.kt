package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementConfigService
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsageService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Covers the allowance that decides whether variables and sequences may be authored.
 */
class VariableSubscriptionGuardTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()

    private fun guard(
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ): VariableSubscriptionGuard
    {
        val accessService = SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
        )

        return VariableSubscriptionGuard(accessService)
    }

    private fun givenUserPlan(planCode: PlanCode, status: SubscriptionStatus = SubscriptionStatus.ACTIVE)
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@VariableSubscriptionGuardTest.appUserId
                this.planCode = planCode.name
                this.subscriptionStatus = status.name
            },
        )
    }

    private fun givenOrganizationPlan()
    {
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(
            OrganizationSubscriptionPolicy().apply {
                this.tierCode = PlanCode.BUSINESS.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.organizationFeatureOverrides(organizationId)).thenReturn(emptyMap())
    }

    @Test
    fun `a free account may not author a personal variable`()
    {
        givenUserPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requirePersonalManagement(appUserId)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.VARIABLES_AND_SEQUENCES, denial.feature)
        assertEquals(PlanCode.PERSONAL, denial.upgradePlanCode)
    }

    @Test
    fun `a personal account may author a personal variable`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        assertDoesNotThrow { guard().requirePersonalManagement(appUserId) }
    }

    @Test
    fun `an organization value is charged to the organization`()
    {
        givenUserPlan(PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow { guard().requireOrganizationManagement(organizationId) }
    }

    @Test
    fun `a suspended subscription may not author`()
    {
        givenUserPlan(PlanCode.PERSONAL, SubscriptionStatus.SUSPENDED)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requirePersonalManagement(appUserId)
        }.denial

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, denial.reason)
    }

    @Test
    fun `report only mode records the refusal and lets the operation through`()
    {
        givenUserPlan(PlanCode.FREE)

        assertDoesNotThrow {
            guard(SubscriptionEnforcementMode.REPORT_ONLY).requirePersonalManagement(appUserId)
        }
    }

    @Test
    fun `disabled enforcement performs no plan work at all`()
    {
        val disabled = guard(SubscriptionEnforcementMode.OFF)

        disabled.requirePersonalManagement(appUserId)
        disabled.requireOrganizationManagement(organizationId)

        verifyNoInteractions(policyService)
    }
}



