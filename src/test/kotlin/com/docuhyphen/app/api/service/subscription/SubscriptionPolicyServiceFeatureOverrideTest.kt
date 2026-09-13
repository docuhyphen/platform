package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.repository.subscription.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.repository.subscription.SubscriptionFeatureEntitlementRepository
import com.docuhyphen.app.api.repository.subscription.UserSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.organization.OrganizationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * A platform-administered feature decision belongs to the owner it was recorded against, and the
 * lookup has to ask about that owner specifically. Reading by id alone would let an organization and
 * an individual account that happen to share an id inherit each other's decisions.
 */
class SubscriptionPolicyServiceFeatureOverrideTest
{
    private val organizationId: UUID = UUID.randomUUID()
    private val appUserId: UUID = UUID.randomUUID()

    private val entitlementRepository: SubscriptionFeatureEntitlementRepository = mock()

    private val service = SubscriptionPolicyService(
        userSubscriptionPolicyRepository = mock<UserSubscriptionPolicyRepository>(),
        organizationSubscriptionPolicyRepository = mock<OrganizationSubscriptionPolicyRepository>(),
        subscriptionFeatureEntitlementRepository = entitlementRepository,
        organizationService = mock<OrganizationService>(),
    )

    @Test
    fun `an organization reads the decisions recorded against that organization`()
    {
        whenever(entitlementRepository.findByOrganizationId(organizationId)).thenReturn(
            listOf(
                organizationEntitlement(PlanFeature.INFORMATION_REQUESTS.name, enabled = true),
                organizationEntitlement(PlanFeature.WORKFLOW_AUTOMATION.name, enabled = false),
            ),
        )

        val overrides = service.featureOverrides(SubscriptionContext.forOrganization(organizationId))

        assertEquals(
            mapOf(
                PlanFeature.INFORMATION_REQUESTS to true,
                PlanFeature.WORKFLOW_AUTOMATION to false,
            ),
            overrides,
        )
    }

    @Test
    fun `an individual account reads the decisions recorded against that account`()
    {
        whenever(entitlementRepository.findByAppUserId(appUserId)).thenReturn(
            listOf(personalEntitlement(PlanFeature.INFORMATION_REQUESTS.name, enabled = true)),
        )

        val overrides = service.featureOverrides(SubscriptionContext.forUser(appUserId))

        assertEquals(mapOf(PlanFeature.INFORMATION_REQUESTS to true), overrides)
    }

    @Test
    fun `an owner never reads the other owner's decisions`()
    {
        val sharedId = UUID.randomUUID()
        whenever(entitlementRepository.findByOrganizationId(sharedId)).thenReturn(
            listOf(organizationEntitlement(PlanFeature.INFORMATION_REQUESTS.name, enabled = true)),
        )
        whenever(entitlementRepository.findByAppUserId(sharedId)).thenReturn(emptyList())

        val personal = service.featureOverrides(SubscriptionContext.forUser(sharedId))

        assertTrue(personal.isEmpty(), "A person must not inherit the decisions of an organization")
        verify(entitlementRepository).findByAppUserId(sharedId)
    }

    @Test
    fun `a code that names no product feature changes nothing`()
    {
        whenever(entitlementRepository.findByAppUserId(appUserId)).thenReturn(
            listOf(
                personalEntitlement("WITHDRAWN_CAPABILITY", enabled = true),
                personalEntitlement(PlanFeature.INFORMATION_REQUESTS.name, enabled = true),
            ),
        )

        val overrides = service.featureOverrides(SubscriptionContext.forUser(appUserId))

        assertEquals(mapOf(PlanFeature.INFORMATION_REQUESTS to true), overrides)
    }

    private fun organizationEntitlement(
        featureCode: String,
        enabled: Boolean,
    ): SubscriptionFeatureEntitlement = SubscriptionFeatureEntitlement().apply {
        this.ownerType = SubscriptionOwnerType.ORGANIZATION.name
        this.organizationId = this@SubscriptionPolicyServiceFeatureOverrideTest.organizationId
        this.featureCode = featureCode
        this.isEnabled = enabled
        this.updatedByAppUserId = UUID.randomUUID()
    }

    private fun personalEntitlement(
        featureCode: String,
        enabled: Boolean,
    ): SubscriptionFeatureEntitlement = SubscriptionFeatureEntitlement().apply {
        this.ownerType = SubscriptionOwnerType.USER.name
        this.appUserId = this@SubscriptionPolicyServiceFeatureOverrideTest.appUserId
        this.featureCode = featureCode
        this.isEnabled = enabled
        this.updatedByAppUserId = UUID.randomUUID()
    }
}
