package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.service.subscription.EffectiveSubscription
import com.docuhyphen.app.api.service.subscription.PlanCatalog
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialFactory
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SubscriptionDtoMapperTest
{
    private val ownerId: UUID = UUID.randomUUID()
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    private fun subscription(
        planCode: PlanCode,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        purchasedSeats: Long? = null,
    ): EffectiveSubscription
    {
        val definition = PlanCatalog.definitionOf(planCode)
        return EffectiveSubscription(
            planCode = planCode,
            ownerType = definition.ownerType,
            ownerId = ownerId,
            status = status,
            features = definition.features,
            limits = definition.limits,
            billingFrequency = null,
            currentPeriodStart = now,
            currentPeriodEnd = now.plusSeconds(86_400),
            gracePeriodEnd = null,
            purchasedSeats = purchasedSeats,
            upgradePlanCode = definition.upgradePlanCode,
        )
    }

    @Test
    fun `an individual plan maps its allowances usage and upgrade path`()
    {
        val dto = SubscriptionDtoMapper.toDto(
            subscription = subscription(PlanCode.FREE),
            usage = SubscriptionUsage(
                newExchangesThisPeriod = 2,
                openExchanges = 1,
                usagePeriodStart = Instant.parse("2026-08-01T00:00:00Z"),
                usagePeriodEnd = Instant.parse("2026-09-01T00:00:00Z"),
            ),
            enforcementMode = SubscriptionEnforcementMode.REPORT_ONLY,
            at = now,
        )

        assertEquals("FREE", dto.planCode)
        assertEquals("USER", dto.ownerType)
        assertEquals(ownerId, dto.ownerId)
        assertEquals("ACTIVE", dto.status)
        assertEquals(
            listOf(
                PlanFeature.DOCUMENT_COMMENTS.name,
                PlanFeature.EXCHANGE_CREATE.name,
            ),
            dto.features,
        )
        assertEquals(5L, dto.limits.maxNewExchangesPerCalendarMonth)
        assertEquals(3L, dto.limits.maxOpenExchanges)
        assertEquals(1L, dto.limits.seatCapacity)
        assertFalse(dto.limits.seatsArePurchased)
        assertEquals(2L, dto.usage.newExchangesThisPeriod)
        assertEquals("2026-09-01T00:00:00Z", dto.usage.usagePeriodEnd)
        assertEquals("PERSONAL", dto.upgradePlanCode)
        assertEquals("REPORT_ONLY", dto.enforcementMode)
        assertTrue(dto.allowsMutations)
    }

    @Test
    fun `an organization plan reports purchased seat capacity and active seats`()
    {
        val dto = SubscriptionDtoMapper.toDto(
            subscription = subscription(PlanCode.BUSINESS, purchasedSeats = 20),
            usage = SubscriptionUsage(activeSeats = 14),
            enforcementMode = SubscriptionEnforcementMode.ENFORCE,
            at = now,
        )

        assertEquals("ORGANIZATION", dto.ownerType)
        assertEquals(20L, dto.limits.seatCapacity)
        assertTrue(dto.limits.seatsArePurchased)
        assertEquals(14L, dto.usage.activeSeats)
        assertNull(dto.upgradePlanCode)
        assertNull(dto.usage.openExchanges)
    }

    @Test
    fun `a suspended owner is reported as unable to make changes`()
    {
        val dto = SubscriptionDtoMapper.toDto(
            subscription = subscription(PlanCode.PERSONAL, SubscriptionStatus.SUSPENDED),
            usage = SubscriptionUsage.NONE,
            enforcementMode = SubscriptionEnforcementMode.ENFORCE,
            at = now,
        )

        assertEquals("SUSPENDED", dto.status)
        assertFalse(dto.allowsMutations)
    }

    @Test
    fun `a refusal maps every structured field the app needs`()
    {
        val denial = SubscriptionDenialFactory.limitReached(
            subscription = subscription(PlanCode.FREE),
            feature = PlanFeature.EXCHANGE_CREATE,
            allowanceDescription = "5 new Exchanges per calendar month.",
            currentValue = 5,
            limit = 5,
        )

        val dto = SubscriptionDtoMapper.toDto(denial)

        assertEquals("PLAN_LIMIT_REACHED", dto.reasonCode)
        assertEquals("FREE", dto.planCode)
        assertEquals("EXCHANGE_CREATE", dto.featureCode)
        assertEquals(5L, dto.currentValue)
        assertEquals(5L, dto.limit)
        assertEquals("PERSONAL", dto.upgradePlanCode)
        assertTrue(dto.errorMessage.contains("5 new Exchanges per calendar month"))
        assertTrue(dto.errorMessage.contains("Personal"))
    }
}

