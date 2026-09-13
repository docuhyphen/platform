package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

/**
 * A runtime request never names its own scope kind: it answers to exactly the owner of its parent
 * Exchange, whether that owner is an organization or a person.
 */
class InformationRequestEntitlementGuardTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()
    private val otherOrganizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()

    @Test
    fun `an organization owned exchange that holds both grants reaches the capability`()
    {
        givenOrganizationPlan(organizationId, commercialGrant = true)
        val guard = guard(grants = organizationRolloutGrant(organizationId))

        assertDoesNotThrow { guard.requireRequestMutation(organizationExchange(organizationId)) }
    }

    @Test
    fun `a personally owned exchange that holds both grants reaches the capability`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard(grants = personalRolloutGrant())

        assertDoesNotThrow { guard.requireRequestMutation(personalExchange(appUserId)) }
    }

    @Test
    fun `the owner consulted is the exchange owner, not another organization`()
    {
        givenOrganizationPlan(organizationId, commercialGrant = true)
        givenOrganizationPlan(otherOrganizationId, commercialGrant = true)
        val guard = guard(grants = organizationRolloutGrant(organizationId))

        val refusal = assertThrows<SubscriptionDenialException> {
            guard.requireRequestMutation(organizationExchange(otherOrganizationId))
        }

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_RELEASED, refusal.denial.reason)
    }

    @Test
    fun `a read answers to the same two decisions as a write`()
    {
        givenUserPlan(commercialGrant = true)
        val ungranted = guard()

        assertThrows<SubscriptionDenialException> {
            ungranted.requireRequestAccess(personalExchange(appUserId))
        }

        val granted = guard(grants = personalRolloutGrant())
        assertDoesNotThrow { granted.requireRequestAccess(personalExchange(appUserId)) }
    }

    @Test
    fun `disabled enforcement does not open a capability that is still being released`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard(mode = SubscriptionEnforcementMode.OFF)

        assertThrows<SubscriptionDenialException> {
            guard.requireRequestMutation(personalExchange(appUserId))
        }
    }

    // ── Commercial-entitlement x rollout-grant truth table ──────────────────────
    //
    // A rollout-gated feature answers to both dimensions unconditionally: neither the
    // commercial grant alone nor the rollout grant alone is enough, and the combination is never
    // softened by the enforcement mode. These cases pin all four combinations plus the two
    // enforcement modes that could otherwise be mistaken for softening one of them.

    @Test
    fun `holding only the rollout grant without the commercial entitlement is denied`()
    {
        givenUserPlan(commercialGrant = false)
        val guard = guard(grants = personalRolloutGrant())

        assertThrows<SubscriptionDenialException> {
            guard.requireRequestMutation(personalExchange(appUserId))
        }
        assertThrows<SubscriptionDenialException> {
            guard.requireRequestAccess(personalExchange(appUserId))
        }
    }

    @Test
    fun `holding only the commercial entitlement without the rollout grant is denied`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard()

        assertThrows<SubscriptionDenialException> {
            guard.requireRequestMutation(personalExchange(appUserId))
        }
        assertThrows<SubscriptionDenialException> {
            guard.requireRequestAccess(personalExchange(appUserId))
        }
    }

    @Test
    fun `holding neither the commercial entitlement nor the rollout grant is denied`()
    {
        givenUserPlan(commercialGrant = false)
        val guard = guard()

        assertThrows<SubscriptionDenialException> {
            guard.requireRequestMutation(personalExchange(appUserId))
        }
        assertThrows<SubscriptionDenialException> {
            guard.requireRequestAccess(personalExchange(appUserId))
        }
    }

    @Test
    fun `report-only enforcement does not open a capability that is still being released`()
    {
        givenUserPlan(commercialGrant = false)
        val guard = guard(mode = SubscriptionEnforcementMode.REPORT_ONLY)

        assertThrows<SubscriptionDenialException> {
            guard.requireRequestMutation(personalExchange(appUserId))
        }
    }

    @Test
    fun `report-only enforcement still reaches the capability once both grants are held`()
    {
        givenUserPlan(commercialGrant = true)
        val guard = guard(grants = personalRolloutGrant(), mode = SubscriptionEnforcementMode.REPORT_ONLY)

        assertDoesNotThrow { guard.requireRequestMutation(personalExchange(appUserId)) }
    }

    // ── Emergency operational suspension ─────────────────────────────────────────
    //
    // Distinct from a billing lapse: a suspended subscription carries no grace window and is
    // checked directly against the live subscription, since this is the one decision that must
    // still reach a request whose execution grant is already frozen.

    @Test
    fun `an active owner is not operationally suspended`()
    {
        givenUserPlan(commercialGrant = true, status = SubscriptionStatus.ACTIVE)
        val guard = guard(grants = personalRolloutGrant())

        assertDoesNotThrow { guard.requireNotOperationallySuspended(personalExchange(appUserId)) }
    }

    @Test
    fun `a suspended owner is denied even when enforcement is disabled`()
    {
        givenUserPlan(commercialGrant = true, status = SubscriptionStatus.SUSPENDED)
        val guard = guard(grants = personalRolloutGrant(), mode = SubscriptionEnforcementMode.OFF)

        assertThrows<SubscriptionDenialException> {
            guard.requireNotOperationallySuspended(personalExchange(appUserId))
        }
    }

    @Test
    fun `a past-due owner is not treated as operationally suspended`()
    {
        givenUserPlan(commercialGrant = true, status = SubscriptionStatus.PAST_DUE)
        val guard = guard(grants = personalRolloutGrant())

        assertDoesNotThrow { guard.requireNotOperationallySuspended(personalExchange(appUserId)) }
    }

    @Test
    fun `a canceled owner is not treated as operationally suspended`()
    {
        givenUserPlan(commercialGrant = true, status = SubscriptionStatus.CANCELED)
        val guard = guard(grants = personalRolloutGrant())

        assertDoesNotThrow { guard.requireNotOperationallySuspended(personalExchange(appUserId)) }
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private fun guard(
        grants: String = "",
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ) = InformationRequestEntitlementGuard(
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

    private fun organizationExchange(owner: UUID) = Exchange().apply { ownerOrganizationId = owner }

    private fun personalExchange(owner: UUID) = Exchange().apply { ownerUserId = owner }

    private fun givenUserPlan(commercialGrant: Boolean, status: SubscriptionStatus = SubscriptionStatus.ACTIVE)
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@InformationRequestEntitlementGuardTest.appUserId
                this.planCode = PlanCode.PERSONAL.name
                this.subscriptionStatus = status.name
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
