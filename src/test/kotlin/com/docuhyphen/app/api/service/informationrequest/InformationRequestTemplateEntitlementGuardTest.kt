package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.service.subscription.FeatureRolloutConfigService
import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementConfigService
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsageService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

/**
 * Which owner answers for a piece of reusable request configuration.
 *
 * The two decisions this reaches, a commercial entitlement recorded against an owner and an
 * operational release grant made for that owner in this deployment, are covered on their own terms
 * elsewhere. What is covered here is the part specific to Templates: that a scope kind consults
 * exactly the owner it names, that a person is a resolvable owner in their own right rather than
 * falling back to an organization or to the platform, and that a scope with no owner is refused
 * rather than reaching neither decision and passing by default.
 */
class InformationRequestTemplateEntitlementGuardTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()
    private val otherOrganizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()

    @Test
    fun `an organization owner that holds both grants reaches the capability`()
    {
        givenOrganizationPlan(organizationId, commercialGrant = true)
        val guard = guard(grants = organizationRolloutGrant(organizationId))

        assertDoesNotThrow {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null,
            )
        }
    }

    @Test
    fun `a person that holds both grants reaches the capability without an organization`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard(grants = personalRolloutGrant())

        assertDoesNotThrow {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId,
            )
        }
    }

    @Test
    fun `the owner consulted is the one the configuration names, not another`()
    {
        givenOrganizationPlan(organizationId, commercialGrant = true)
        givenOrganizationPlan(otherOrganizationId, commercialGrant = true)
        // Released to one organization only, so naming the other has to be refused even though both
        // hold the commercial grant.
        val guard = guard(grants = organizationRolloutGrant(organizationId))

        val refusal = assertThrows<SubscriptionDenialException> {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.ORGANIZATION, otherOrganizationId, null,
            )
        }

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_RELEASED, refusal.denial.reason)
    }

    @Test
    fun `a personal grant does not reach an organization that names the same person`()
    {
        givenUserPlan(commercialGrant = true)
        givenOrganizationPlan(organizationId, commercialGrant = true)
        val guard = guard(grants = personalRolloutGrant())

        assertThrows<SubscriptionDenialException> {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null,
            )
        }
    }

    @Test
    fun `a scope with no owner to answer for it is refused`()
    {
        val guard = guard()

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            guard.requireTemplateMutation(InformationRequestTemplateScopeKind.PLATFORM, null, null)
        }

        assertTrue(
            refusal.message.contains("PLATFORM"),
            "The refusal names the scope with no owner: ${refusal.message}",
        )
    }

    @Test
    fun `a read answers to the same two decisions as a write`()
    {
        givenUserPlan(commercialGrant = true)
        val ungranted = guard()

        assertThrows<SubscriptionDenialException> {
            ungranted.requireTemplateAccess(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId,
            )
        }

        val granted = guard(grants = personalRolloutGrant())
        assertDoesNotThrow {
            granted.requireTemplateAccess(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId,
            )
        }
    }

    @Test
    fun `disabled enforcement does not open a capability that is still being released`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard(mode = SubscriptionEnforcementMode.OFF)

        assertThrows<SubscriptionDenialException> {
            guard.requireTemplateMutation(
                InformationRequestTemplateScopeKind.PERSONAL, null, appUserId,
            )
        }
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private fun guard(
        grants: String = "",
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ) = InformationRequestTemplateEntitlementGuard(
        SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
            featureRolloutConfigService = FeatureRolloutConfigService(Optional.of(grants)),
        ),
    )

    private fun givenUserPlan(commercialGrant: Boolean)
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@InformationRequestTemplateEntitlementGuardTest.appUserId
                this.planCode = PlanCode.PERSONAL.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.featureOverrides(SubscriptionContext.forUser(appUserId)))
            .thenReturn(if (commercialGrant) mapOf(GATED_FEATURE to true) else emptyMap())
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
            .thenReturn(if (commercialGrant) mapOf(GATED_FEATURE to true) else emptyMap())
    }

    private fun personalRolloutGrant() = "$GATED_FEATURE:USER:$appUserId"

    private fun organizationRolloutGrant(owner: UUID) = "$GATED_FEATURE:ORGANIZATION:$owner"

    private companion object
    {
        val GATED_FEATURE = PlanFeature.INFORMATION_REQUESTS
    }
}
