package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

/**
 * Covers a capability under controlled release, which is governed by two independent decisions:
 * a commercial grant recorded against the owner, and an operational readiness grant for the same
 * owner in this deployment. Holding one without the other is refused.
 */
class FeatureRolloutGateTest
{
    private val appUserId: UUID = UUID.fromString("3c9e5a71-0d42-4b8f-9e16-77a2c4d5e6f7")
    private val organizationId: UUID = UUID.fromString("6b0d4e82-1f53-4a97-8c25-88b3d5e6f708")

    private val gatedFeature = PlanFeature.INFORMATION_REQUESTS
    private val releasedFeature = PlanFeature.BLUEPRINT_MANAGE

    private val policyService: SubscriptionPolicyService = mock()

    private fun service(
        grants: String = "",
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ): SubscriptionAccessService
    {
        return SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
            featureRolloutConfigService = FeatureRolloutConfigService(Optional.of(grants)),
        )
    }

    private fun givenUserPlan(commercialGrant: Boolean)
    {
        val policy = UserSubscriptionPolicy().apply {
            this.appUserId = this@FeatureRolloutGateTest.appUserId
            this.planCode = PlanCode.PERSONAL.name
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(policy)
        whenever(policyService.featureOverrides(SubscriptionContext.forUser(appUserId)))
            .thenReturn(if (commercialGrant) mapOf(gatedFeature to true) else emptyMap())
    }

    private fun givenOrganizationPlan(commercialGrant: Boolean)
    {
        val policy = OrganizationSubscriptionPolicy().apply {
            this.tierCode = PlanCode.BUSINESS.name
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(policy)
        whenever(policyService.featureOverrides(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(if (commercialGrant) mapOf(gatedFeature to true) else emptyMap())
    }

    private fun userContext() = SubscriptionContext.forUser(appUserId)

    private fun organizationContext() = SubscriptionContext.forOrganization(organizationId)

    private fun userRolloutGrant() = "$gatedFeature:USER:$appUserId"

    @Test
    fun `a capability under controlled release is refused when neither grant exists`()
    {
        givenUserPlan(commercialGrant = false)

        val denial = assertThrows<SubscriptionDenialException> {
            service().requireFeature(userContext(), gatedFeature)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
    }

    @Test
    fun `the commercial grant alone is refused for want of an operational grant`()
    {
        givenUserPlan(commercialGrant = true)

        val denial = assertThrows<SubscriptionDenialException> {
            service().requireFeature(userContext(), gatedFeature)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_RELEASED, denial.reason)
        assertEquals(gatedFeature, denial.feature)
    }

    @Test
    fun `the operational grant alone is refused for want of a commercial grant`()
    {
        givenUserPlan(commercialGrant = false)

        val denial = assertThrows<SubscriptionDenialException> {
            service(grants = userRolloutGrant()).requireFeature(userContext(), gatedFeature)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
    }

    @Test
    fun `both grants held by the same owner reach the capability`()
    {
        givenUserPlan(commercialGrant = true)

        assertDoesNotThrow {
            service(grants = userRolloutGrant()).requireFeature(userContext(), gatedFeature)
        }
    }

    @Test
    fun `an operational grant made for one owner does not reach another`()
    {
        givenUserPlan(commercialGrant = true)
        givenOrganizationPlan(commercialGrant = true)

        val service = service(grants = userRolloutGrant())

        assertDoesNotThrow { service.requireFeature(userContext(), gatedFeature) }

        val denial = assertThrows<SubscriptionDenialException> {
            service.requireFeature(organizationContext(), gatedFeature)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_RELEASED, denial.reason)
    }

    @Test
    fun `report only enforcement does not let a capability under controlled release through`()
    {
        givenUserPlan(commercialGrant = true)

        assertThrows<SubscriptionDenialException> {
            service(mode = SubscriptionEnforcementMode.REPORT_ONLY)
                .requireFeature(userContext(), gatedFeature)
        }

        givenUserPlan(commercialGrant = false)

        assertThrows<SubscriptionDenialException> {
            service(grants = userRolloutGrant(), mode = SubscriptionEnforcementMode.REPORT_ONLY)
                .requireFeature(userContext(), gatedFeature)
        }
    }

    @Test
    fun `disabled enforcement does not let a capability under controlled release through`()
    {
        givenUserPlan(commercialGrant = true)

        assertThrows<SubscriptionDenialException> {
            service(mode = SubscriptionEnforcementMode.OFF).requireFeature(userContext(), gatedFeature)
        }

        givenUserPlan(commercialGrant = false)

        assertThrows<SubscriptionDenialException> {
            service(grants = userRolloutGrant(), mode = SubscriptionEnforcementMode.OFF)
                .requireFeature(userContext(), gatedFeature)
        }
    }

    @Test
    fun `disabled enforcement still lets a released capability through`()
    {
        givenUserPlan(commercialGrant = false)

        assertDoesNotThrow {
            service(mode = SubscriptionEnforcementMode.OFF)
                .requireFeature(userContext(), PlanFeature.WORKFLOW_AUTOMATION)
        }
    }

    @Test
    fun `a released capability is unaffected by the rollout configuration`()
    {
        givenUserPlan(commercialGrant = false)

        assertDoesNotThrow { service().requireFeature(userContext(), releasedFeature) }
        assertTrue(service().isFeatureAvailable(userContext(), releasedFeature))
    }

    @Test
    fun `the availability read answers the same two grants without refusing`()
    {
        givenUserPlan(commercialGrant = true)

        assertFalse(
            service().isFeatureAvailable(userContext(), gatedFeature),
            "A commercial grant without an operational grant is not available",
        )
        assertTrue(
            service(grants = userRolloutGrant()).isFeatureAvailable(userContext(), gatedFeature),
        )

        givenUserPlan(commercialGrant = false)

        assertFalse(
            service(grants = userRolloutGrant()).isFeatureAvailable(userContext(), gatedFeature),
            "An operational grant without a commercial grant is not available",
        )
    }

    @Test
    fun `the capability is declared as being under controlled release`()
    {
        assertTrue(gatedFeature.requiresRolloutGrant)
        assertFalse(releasedFeature.requiresRolloutGrant)
    }

    @Test
    fun `the resolved commercial position still reports the entitlement on its own`()
    {
        givenUserPlan(commercialGrant = true)

        assertTrue(
            service().resolve(userContext()).hasFeature(gatedFeature),
            "The commercial position is what the owner is entitled to, which the grant changed",
        )
    }

    @Test
    fun `the session contract reports the capability only once both grants exist`()
    {
        givenUserPlan(commercialGrant = true)

        assertFalse(
            sessionFeatures(service()).contains(gatedFeature.name),
            "A commercial grant alone must not advertise a capability the owner cannot reach",
        )
        assertTrue(
            sessionFeatures(service(grants = userRolloutGrant())).contains(gatedFeature.name),
        )
    }

    @Test
    fun `the session contract still reports every released capability`()
    {
        givenUserPlan(commercialGrant = true)

        assertTrue(sessionFeatures(service()).contains(releasedFeature.name))
    }

    private fun sessionFeatures(accessService: SubscriptionAccessService): List<String>
    {
        val described = SessionSubscriptionService(accessService).describe(appUserId, null)
        return requireNotNull(described) { "The session contract could not be described" }.features
    }
}
