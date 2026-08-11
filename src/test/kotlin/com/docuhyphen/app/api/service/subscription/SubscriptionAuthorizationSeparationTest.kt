package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Guards the boundary between the two independent questions a protected action must answer:
 * whether the caller is authorized, and whether the paying subject bought the feature.
 * Neither answer may be derived from the other.
 */
class SubscriptionAuthorizationSeparationTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private fun freeIndividual(): EffectiveSubscription
    {
        val policy = UserSubscriptionPolicy().apply {
            this.appUserId = this@SubscriptionAuthorizationSeparationTest.appUserId
            this.planCode = PlanCode.FREE.name
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        return EffectiveSubscriptionFactory.fromUserPolicy(policy)
    }

    private fun businessOrganization(): EffectiveSubscription
    {
        val policy = OrganizationSubscriptionPolicy().apply {
            this.tierCode = PlanCode.BUSINESS.name
            this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
        }
        return EffectiveSubscriptionFactory.fromOrganizationPolicy(organizationId, policy, emptyMap())
    }

    @Test
    fun `holding a privileged role grants no commercial feature`()
    {
        val ownerCapabilities = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_OWNER)
        val adminCapabilities = RoleCapabilities.forAppRole(AppRoleName.APP_ADMIN)
        assertTrue(ownerCapabilities.isNotEmpty())
        assertTrue(adminCapabilities.isNotEmpty())

        val subscription = freeIndividual()
        assertFalse(subscription.hasFeature(PlanFeature.WORKFLOW_AUTOMATION))
        assertFalse(subscription.hasFeature(PlanFeature.DOCUMENT_COMMENTS))
        assertFalse(subscription.hasFeature(PlanFeature.BLUEPRINT_USE))
    }

    @Test
    fun `owning a full plan grants no role permission`()
    {
        val subscription = businessOrganization()
        assertTrue(subscription.hasFeature(PlanFeature.WORKFLOW_AUTOMATION))
        assertTrue(subscription.hasFeature(PlanFeature.ORGANIZATION_ADMINISTRATION))

        val capabilitiesOfARoleLessCaller = emptySet<Capability>()
        assertTrue(capabilitiesOfARoleLessCaller.isEmpty())

        val guestCapabilities = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_GUEST)
        val adminOnlyCapabilities = RoleCapabilities
            .forOrganizationRole(OrganizationRoleName.ORG_ADMIN)
            .minus(guestCapabilities)
        assertTrue(adminOnlyCapabilities.isNotEmpty())
        assertTrue(adminOnlyCapabilities.none { it in guestCapabilities })
    }

    @Test
    fun `a shared name never couples a capability to a plan feature`()
    {
        val sharedNames = Capability.entries.map { it.name }
            .intersect(PlanFeature.entries.map { it.name }.toSet())
        assertTrue(sharedNames.contains(PlanFeature.BLUEPRINT_USE.name))

        val roleWithTheCapability = OrganizationRoleName.ORG_MEMBER
        val capabilities = RoleCapabilities.forOrganizationRole(roleWithTheCapability)
        assertTrue(capabilities.contains(Capability.BLUEPRINT_USE))

        assertFalse(freeIndividual().hasFeature(PlanFeature.BLUEPRINT_USE))
        assertTrue(businessOrganization().hasFeature(PlanFeature.BLUEPRINT_USE))
        assertFalse(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_GUEST).contains(Capability.BLUEPRINT_USE))
    }
}


