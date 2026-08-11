package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EffectiveSubscriptionTest
{
    private fun subscription(
        planCode: PlanCode,
        status: SubscriptionStatus,
        gracePeriodEnd: Instant? = null,
        currentPeriodEnd: Instant? = null,
        purchasedSeats: Long? = null,
    ): EffectiveSubscription
    {
        val definition = PlanCatalog.definitionOf(planCode)
        return EffectiveSubscription(
            planCode = planCode,
            ownerType = definition.ownerType,
            ownerId = UUID.randomUUID(),
            status = status,
            features = definition.features,
            limits = definition.limits,
            billingFrequency = null,
            currentPeriodStart = null,
            currentPeriodEnd = currentPeriodEnd,
            gracePeriodEnd = gracePeriodEnd,
            purchasedSeats = purchasedSeats,
            upgradePlanCode = definition.upgradePlanCode,
        )
    }

    @Test
    fun `trialing owners may mutate only until the trial ends while active owners may mutate`()
    {
        val now = Instant.parse("2026-08-10T00:00:00Z")
        assertTrue(
            subscription(
                PlanCode.PERSONAL,
                SubscriptionStatus.TRIALING,
                currentPeriodEnd = now.plusSeconds(1),
            ).allowsMutations(now),
        )
        assertFalse(
            subscription(
                PlanCode.PERSONAL,
                SubscriptionStatus.TRIALING,
                currentPeriodEnd = now,
            ).allowsMutations(now),
        )
        assertTrue(subscription(PlanCode.PERSONAL, SubscriptionStatus.ACTIVE).allowsMutations())
    }

    @Test
    fun `past due owners may mutate only until the grace period ends`()
    {
        val now = Instant.parse("2026-08-10T00:00:00Z")

        assertTrue(
            subscription(
                planCode = PlanCode.PERSONAL,
                status = SubscriptionStatus.PAST_DUE,
                gracePeriodEnd = now.plusSeconds(3600),
            ).allowsMutations(now),
        )

        assertFalse(
            subscription(
                planCode = PlanCode.PERSONAL,
                status = SubscriptionStatus.PAST_DUE,
                gracePeriodEnd = now.minusSeconds(1),
            ).allowsMutations(now),
        )

        assertFalse(
            subscription(PlanCode.PERSONAL, SubscriptionStatus.PAST_DUE).allowsMutations(now),
        )
    }

    @Test
    fun `canceled owner may mutate until paid period ends and suspended owner may not`()
    {
        val now = Instant.parse("2026-08-10T00:00:00Z")
        assertTrue(subscription(PlanCode.PERSONAL, SubscriptionStatus.CANCELED, currentPeriodEnd = now.plusSeconds(1)).allowsMutations(now))
        assertFalse(subscription(PlanCode.PERSONAL, SubscriptionStatus.CANCELED, currentPeriodEnd = now).allowsMutations(now))
        assertFalse(subscription(PlanCode.PERSONAL, SubscriptionStatus.SUSPENDED).allowsMutations(now))
        assertTrue(SubscriptionStatus.SUSPENDED.permitsReads)
        assertTrue(SubscriptionStatus.CANCELED.permitsReads)
    }

    @Test
    fun `business seat capacity comes from purchased seats`()
    {
        assertEquals(
            25L,
            subscription(PlanCode.BUSINESS, SubscriptionStatus.ACTIVE, purchasedSeats = 25).effectiveSeatCapacity(),
        )
        assertNull(
            subscription(PlanCode.BUSINESS, SubscriptionStatus.ACTIVE).effectiveSeatCapacity(),
        )
    }

    @Test
    fun `individual seat capacity comes from the plan`()
    {
        assertEquals(
            1L,
            subscription(PlanCode.FREE, SubscriptionStatus.ACTIVE, purchasedSeats = 99).effectiveSeatCapacity(),
        )
    }

    @Test
    fun `feature checks reflect the resolved feature set`()
    {
        val free = subscription(PlanCode.FREE, SubscriptionStatus.ACTIVE)
        assertTrue(free.hasFeature(PlanFeature.EXCHANGE_CREATE))
        assertFalse(free.hasFeature(PlanFeature.MULTIPLE_PARTICIPANTS))
    }
}

