package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class EffectiveSubscriptionFactoryTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private fun userPolicy(
        planCode: String = PlanCode.FREE.name,
        status: String = SubscriptionStatus.ACTIVE.name,
        billingFrequency: String? = null,
        gracePeriodEnd: Instant? = null,
    ): UserSubscriptionPolicy
    {
        return UserSubscriptionPolicy().apply {
            this.appUserId = this@EffectiveSubscriptionFactoryTest.appUserId
            this.planCode = planCode
            this.subscriptionStatus = status
            this.billingFrequency = billingFrequency
            this.gracePeriodEnd = gracePeriodEnd?.let { Timestamp.from(it) }
        }
    }

    private fun organizationPolicy(
        tierCode: String = PlanCode.BUSINESS.name,
        status: String = SubscriptionStatus.ACTIVE.name,
        maxUsers: Long? = null,
    ): OrganizationSubscriptionPolicy
    {
        return OrganizationSubscriptionPolicy().apply {
            this.tierCode = tierCode
            this.subscriptionStatus = status
            this.maxUsers = maxUsers
        }
    }

    @Test
    fun `individual policy resolves plan features limits and owner`()
    {
        val resolved = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(planCode = PlanCode.PERSONAL.name, billingFrequency = "ANNUAL"),
            featureOverrides = emptyMap(),
        )

        assertEquals(PlanCode.PERSONAL, resolved.planCode)
        assertEquals(SubscriptionOwnerType.USER, resolved.ownerType)
        assertEquals(appUserId, resolved.ownerId)
        assertEquals(BillingFrequency.ANNUAL, resolved.billingFrequency)
        assertEquals(PlanCatalog.definitionOf(PlanCode.PERSONAL).features, resolved.features)
        assertNull(resolved.limits.maxNewExchangesPerCalendarMonth)
    }

    @Test
    fun `free individual policy keeps the free allowances`()
    {
        val resolved = EffectiveSubscriptionFactory.fromUserPolicy(userPolicy(), emptyMap())

        assertEquals(PlanCode.FREE, resolved.planCode)
        assertEquals(5L, resolved.limits.maxNewExchangesPerCalendarMonth)
        assertEquals(3L, resolved.limits.maxOpenExchanges)
        assertEquals(PlanCode.PERSONAL, resolved.upgradePlanCode)
    }

    @Test
    fun `organization policy resolves business with purchased seats`()
    {
        val resolved = EffectiveSubscriptionFactory.fromOrganizationPolicy(
            organizationId = organizationId,
            policy = organizationPolicy(maxUsers = 12),
            featureOverrides = emptyMap(),
        )

        assertEquals(PlanCode.BUSINESS, resolved.planCode)
        assertEquals(SubscriptionOwnerType.ORGANIZATION, resolved.ownerType)
        assertEquals(organizationId, resolved.ownerId)
        assertEquals(12L, resolved.purchasedSeats)
        assertEquals(12L, resolved.effectiveSeatCapacity())
    }

    @Test
    fun `organization overrides are applied on top of the plan defaults`()
    {
        val resolved = EffectiveSubscriptionFactory.fromOrganizationPolicy(
            organizationId = organizationId,
            policy = organizationPolicy(),
            featureOverrides = mapOf(
                PlanFeature.WORKFLOW_AUTOMATION to false,
                PlanFeature.AUDIT_GOVERNANCE to false,
            ),
        )

        assertFalse(resolved.hasFeature(PlanFeature.WORKFLOW_AUTOMATION))
        assertFalse(resolved.hasFeature(PlanFeature.AUDIT_GOVERNANCE))
        assertTrue(resolved.hasFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS))
    }

    @Test
    fun `individual overrides are applied on top of the plan defaults`()
    {
        val resolved = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(planCode = PlanCode.PERSONAL.name),
            featureOverrides = mapOf(
                PlanFeature.INFORMATION_REQUESTS to true,
                PlanFeature.DOCUMENT_VERSION_HISTORY to false,
            ),
        )

        assertTrue(
            resolved.hasFeature(PlanFeature.INFORMATION_REQUESTS),
            "A granted feature the plan omits must reach the individual account",
        )
        assertFalse(
            resolved.hasFeature(PlanFeature.DOCUMENT_VERSION_HISTORY),
            "A withdrawn feature must leave the individual account",
        )
        assertTrue(resolved.hasFeature(PlanFeature.BLUEPRINT_MANAGE))
    }

    @Test
    fun `an override never mutates the shared plan definition`()
    {
        EffectiveSubscriptionFactory.fromOrganizationPolicy(
            organizationId = organizationId,
            policy = organizationPolicy(),
            featureOverrides = mapOf(PlanFeature.WORKFLOW_AUTOMATION to false),
        )

        assertTrue(
            PlanCatalog.definitionOf(PlanCode.BUSINESS).includes(PlanFeature.WORKFLOW_AUTOMATION),
        )

        EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(planCode = PlanCode.PERSONAL.name),
            featureOverrides = mapOf(PlanFeature.INFORMATION_REQUESTS to false),
        )

        assertTrue(
            PlanCatalog.definitionOf(PlanCode.PERSONAL).includes(PlanFeature.INFORMATION_REQUESTS),
        )
    }

    @Test
    fun `a plan that cannot belong to the owner falls back to that owner default`()
    {
        val resolvedUser = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(planCode = PlanCode.BUSINESS.name),
            featureOverrides = emptyMap(),
        )
        assertEquals(PlanCatalog.DEFAULT_USER_PLAN, resolvedUser.planCode)

        val resolvedOrganization = EffectiveSubscriptionFactory.fromOrganizationPolicy(
            organizationId = organizationId,
            policy = organizationPolicy(tierCode = PlanCode.FREE.name),
            featureOverrides = emptyMap(),
        )
        assertEquals(PlanCatalog.DEFAULT_ORGANIZATION_PLAN, resolvedOrganization.planCode)
    }

    @Test
    fun `an unknown persisted plan or status degrades predictably`()
    {
        val resolved = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(planCode = "LEGACY_TIER", status = "WHAT"),
            featureOverrides = emptyMap(),
        )

        assertEquals(PlanCatalog.DEFAULT_USER_PLAN, resolved.planCode)
        assertEquals(SubscriptionStatus.ACTIVE, resolved.status)
    }

    @Test
    fun `status clamps mutations while reads always remain available`()
    {
        val now = Instant.parse("2026-08-10T12:00:00Z")

        val suspended = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(planCode = PlanCode.PERSONAL.name, status = SubscriptionStatus.SUSPENDED.name),
            featureOverrides = emptyMap(),
        )
        assertFalse(suspended.allowsMutations(now))
        assertTrue(suspended.status.permitsReads)

        val inGracePeriod = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(
                planCode = PlanCode.PERSONAL.name,
                status = SubscriptionStatus.PAST_DUE.name,
                gracePeriodEnd = now.plusSeconds(60),
            ),
            featureOverrides = emptyMap(),
        )
        assertTrue(inGracePeriod.allowsMutations(now))

        val pastGracePeriod = EffectiveSubscriptionFactory.fromUserPolicy(
            userPolicy(
                planCode = PlanCode.PERSONAL.name,
                status = SubscriptionStatus.PAST_DUE.name,
                gracePeriodEnd = now.minusSeconds(60),
            ),
            featureOverrides = emptyMap(),
        )
        assertFalse(pastGracePeriod.allowsMutations(now))
    }
}

