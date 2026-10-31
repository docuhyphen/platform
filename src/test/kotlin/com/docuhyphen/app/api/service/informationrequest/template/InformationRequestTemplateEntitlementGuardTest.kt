package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.subscription.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestTemplateEntitlementGuardTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()
    private val otherOrganizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()

    @Test
    fun `an organization owner with an admin entitlement reaches the capability`()
    {
        givenOrganizationPlan(organizationId, commercialGrant = true)
        val guard = guard()

        assertDoesNotThrow {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null,
            )
        }
    }

    @Test
    fun `a person with an admin entitlement reaches the capability without an organization`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard()

        assertDoesNotThrow {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId, null,
            )
        }
    }

    @Test
    fun `the Personal plan includes information requests without an override`()
    {
        givenUserPlan(commercialGrant = null)

        assertDoesNotThrow {
            guard().requireTemplateMutation(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId, null,
            )
        }
    }

    @Test
    fun `an active organization entitlement covers a personal template`()
    {
        givenUserPlan(commercialGrant = false)
        givenOrganizationPlan(organizationId, commercialGrant = true)

        assertDoesNotThrow {
            guard().requireTemplateMutation(
                InformationRequestTemplateScopeKind.PERSONAL,
                null,
                appUserId,
                organizationId,
            )
        }
    }

    @Test
    fun `the owner consulted is the one the configuration names, not another`()
    {
        givenOrganizationPlan(organizationId, commercialGrant = true)
        givenOrganizationPlan(otherOrganizationId, commercialGrant = false)
        val guard = guard()

        val refusal = assertThrows<SubscriptionDenialException> {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.ORGANIZATION, otherOrganizationId, null, null,
            )
        }

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, refusal.denial.reason)
    }

    @Test
    fun `a personal entitlement does not reach an organization`()
    {
        givenUserPlan(commercialGrant = true)
        givenOrganizationPlan(organizationId, commercialGrant = false)
        val guard = guard()

        assertThrows<SubscriptionDenialException> {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null,
            )
        }
    }

    @Test
    fun `platform content answers to no subscription owner`()
    {
        val guard = guard()

        assertDoesNotThrow {
            guard.requireTemplateMutation(InformationRequestTemplateScopeKind.PLATFORM, null, null, null)
            guard.requireTemplateAccess(InformationRequestTemplateScopeKind.PLATFORM, null, null, organizationId)
        }

        verifyNoInteractions(policyService)
    }

    @Test
    fun `a read answers to the same entitlement as a write`()
    {
        givenUserPlan(commercialGrant = true)
        assertDoesNotThrow {
            guard().requireTemplateAccess(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId, null,
            )
        }
    }

    @Test
    fun `disabled enforcement allows an entitled capability`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard(mode = SubscriptionEnforcementMode.OFF)

        assertDoesNotThrow {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId, null,
            )
        }
    }

    private fun guard(
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ) = InformationRequestTemplateEntitlementGuard(
        SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
        ),
    )

    private fun givenUserPlan(commercialGrant: Boolean?)
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@InformationRequestTemplateEntitlementGuardTest.appUserId
                this.planCode = PlanCode.PERSONAL.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.featureOverrides(SubscriptionContext.forUser(appUserId)))
            .thenReturn(commercialGrant?.let { mapOf(GATED_FEATURE to it) } ?: emptyMap())
    }

    private fun givenOrganizationPlan(owner: UUID, commercialGrant: Boolean)
    {
        whenever(policyService.findOrganizationPolicy(owner)).thenReturn(
            OrganizationSubscriptionPolicy().apply {
                this.tierCode = PlanCode.BUSINESS.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.featureOverrides(SubscriptionContext.forOrganization(owner)))
            .thenReturn(mapOf(GATED_FEATURE to commercialGrant))
    }

    private companion object
    {
        val GATED_FEATURE = PlanFeature.INFORMATION_REQUESTS
    }
}
