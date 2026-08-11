package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.auth.UserRoleService
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
 * Covers the allowance that decides whether an Exchange may be started from a Blueprint.
 */
class BlueprintSubscriptionGuardTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()
    private val userRoleService: UserRoleService = mock()

    private fun guard(
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ): BlueprintSubscriptionGuard
    {
        val accessService = SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
        )

        return BlueprintSubscriptionGuard(accessService, userRoleService)
    }

    private fun givenUserPlan(planCode: PlanCode)
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@BlueprintSubscriptionGuardTest.appUserId
                this.planCode = planCode.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
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
    fun `a free account may not start an exchange from a blueprint`()
    {
        givenUserPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireBlueprintUse(appUserId, null)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.BLUEPRINT_USE, denial.feature)
        assertEquals(PlanCode.PERSONAL, denial.upgradePlanCode)
    }

    @Test
    fun `a personal account may start an exchange from a blueprint`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        assertDoesNotThrow { guard().requireBlueprintUse(appUserId, null) }
    }

    @Test
    fun `an organization member uses the plan of the selected organization`()
    {
        givenUserPlan(PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow { guard().requireBlueprintUse(appUserId, organizationId) }
    }

    @Test
    fun `curating the platform catalogue is not charged against a personal plan`()
    {
        givenUserPlan(PlanCode.FREE)
        whenever(userRoleService.isAppAdmin(appUserId)).thenReturn(true)

        assertDoesNotThrow { guard().requireBlueprintUse(appUserId, null) }
    }

    @Test
    fun `report only mode records the refusal and lets the read through`()
    {
        givenUserPlan(PlanCode.FREE)

        assertDoesNotThrow {
            guard(SubscriptionEnforcementMode.REPORT_ONLY).requireBlueprintUse(appUserId, null)
        }
    }

    @Test
    fun `a free account may not author a personal blueprint`()
    {
        givenUserPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireBlueprintManagement(appUserId, BlueprintScope.PERSONAL, null)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.BLUEPRINT_MANAGE, denial.feature)
        assertEquals(PlanCode.PERSONAL, denial.upgradePlanCode)
    }

    @Test
    fun `a personal account may author a personal blueprint`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        assertDoesNotThrow {
            guard().requireBlueprintManagement(appUserId, BlueprintScope.PERSONAL, null)
        }
    }

    @Test
    fun `an organization blueprint is charged to the organization that owns it`()
    {
        givenUserPlan(PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow {
            guard().requireBlueprintManagement(appUserId, BlueprintScope.ORG, organizationId)
        }
    }

    @Test
    fun `curating the platform catalogue is not charged against a personal plan when authoring`()
    {
        givenUserPlan(PlanCode.FREE)

        assertDoesNotThrow {
            guard().requireBlueprintManagement(appUserId, BlueprintScope.APP, null)
        }
    }

    @Test
    fun `a suspended subscription may not author a blueprint`()
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@BlueprintSubscriptionGuardTest.appUserId
                this.planCode = PlanCode.PERSONAL.name
                this.subscriptionStatus = SubscriptionStatus.SUSPENDED.name
            },
        )

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireBlueprintManagement(appUserId, BlueprintScope.PERSONAL, null)
        }.denial

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, denial.reason)
    }

    @Test
    fun `report only mode records an authoring refusal and lets the change through`()
    {
        givenUserPlan(PlanCode.FREE)

        assertDoesNotThrow {
            guard(SubscriptionEnforcementMode.REPORT_ONLY)
                .requireBlueprintManagement(appUserId, BlueprintScope.PERSONAL, null)
        }
    }

    @Test
    fun `disabled enforcement performs no plan work at all`()
    {
        val disabled = guard(SubscriptionEnforcementMode.OFF)

        disabled.requireBlueprintUse(appUserId, null)
        disabled.requireBlueprintManagement(appUserId, BlueprintScope.PERSONAL, null)

        verifyNoInteractions(policyService)
        verifyNoInteractions(userRoleService)
    }
}



