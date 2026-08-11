package com.docuhyphen.app.api.service.documentlibrary

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
 * Covers the allowance that decides whether the Document Library may be read or changed.
 */
class DocumentLibrarySubscriptionGuardTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()
    private val userRoleService: UserRoleService = mock()

    private fun guard(
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ): DocumentLibrarySubscriptionGuard
    {
        val accessService = SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
        )

        return DocumentLibrarySubscriptionGuard(accessService, userRoleService)
    }

    private fun givenUserPlan(userId: UUID, planCode: PlanCode)
    {
        whenever(policyService.findUserPolicy(userId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = userId
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
    fun `a free account may not browse the library`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireLibraryUse(appUserId, null)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.DOCUMENT_LIBRARY_USE, denial.feature)
        assertEquals(PlanCode.PERSONAL, denial.upgradePlanCode)
    }

    @Test
    fun `a personal account may browse and author the library`()
    {
        givenUserPlan(appUserId, PlanCode.PERSONAL)

        assertDoesNotThrow { guard().requireLibraryUse(appUserId, null) }
        assertDoesNotThrow {
            guard().requireEntryManagement(appUserId, BlueprintScope.PERSONAL, null)
        }
    }

    @Test
    fun `a free account may not author an entry`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireEntryManagement(appUserId, BlueprintScope.PERSONAL, null)
        }.denial

        assertEquals(PlanFeature.DOCUMENT_LIBRARY_MANAGE, denial.feature)
    }

    @Test
    fun `browsing with an organization selected uses the plan of that organization`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow { guard().requireLibraryUse(appUserId, organizationId) }
    }

    @Test
    fun `an organization entry is charged to the organization and not to the reader`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow {
            guard().requireEntryUse(appUserId, BlueprintScope.ORG, organizationId)
        }
    }

    @Test
    fun `an entry owned by somebody else is still charged to the caller reading it`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)
        givenUserPlan(otherUserId, PlanCode.PERSONAL)

        assertThrows<SubscriptionDenialException> {
            guard().requireEntryUse(appUserId, BlueprintScope.PERSONAL, null)
        }
    }

    @Test
    fun `curating the platform catalogue is not charged against a personal plan`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)

        assertDoesNotThrow {
            guard().requireEntryManagement(appUserId, BlueprintScope.APP, null)
        }
    }

    @Test
    fun `a platform administrator is never charged for library work`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)
        whenever(userRoleService.isAppAdmin(appUserId)).thenReturn(true)

        assertDoesNotThrow { guard().requireLibraryUse(appUserId, null) }
        assertDoesNotThrow {
            guard().requireEntryManagement(appUserId, BlueprintScope.PERSONAL, null)
        }
    }

    @Test
    fun `a suspended subscription may still read but may not author`()
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@DocumentLibrarySubscriptionGuardTest.appUserId
                this.planCode = PlanCode.PERSONAL.name
                this.subscriptionStatus = SubscriptionStatus.SUSPENDED.name
            },
        )

        assertDoesNotThrow { guard().requireLibraryUse(appUserId, null) }

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireEntryManagement(appUserId, BlueprintScope.PERSONAL, null)
        }.denial

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, denial.reason)
    }

    @Test
    fun `report only mode records the refusal and lets the operation through`()
    {
        givenUserPlan(appUserId, PlanCode.FREE)

        assertDoesNotThrow {
            guard(SubscriptionEnforcementMode.REPORT_ONLY).requireLibraryUse(appUserId, null)
        }
    }

    @Test
    fun `disabled enforcement performs no plan work at all`()
    {
        val disabled = guard(SubscriptionEnforcementMode.OFF)

        disabled.requireLibraryUse(appUserId, null)
        disabled.requireEntryUse(appUserId, BlueprintScope.PERSONAL, null)
        disabled.requireEntryManagement(appUserId, BlueprintScope.PERSONAL, null)

        verifyNoInteractions(policyService)
        verifyNoInteractions(userRoleService)
    }
}



