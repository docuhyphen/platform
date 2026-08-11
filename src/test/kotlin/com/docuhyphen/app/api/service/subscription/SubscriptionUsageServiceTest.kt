package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SubscriptionUsageServiceTest
{
    private val exchangeUsageCounter: ExchangeUsageCounter = mock()
    private val organizationSeatCounter: OrganizationSeatCounter = mock()
    private val service = SubscriptionUsageService(exchangeUsageCounter, organizationSeatCounter)

    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private fun subscription(planCode: PlanCode, ownerId: UUID): EffectiveSubscription
    {
        val definition = PlanCatalog.definitionOf(planCode)
        return EffectiveSubscription(
            planCode = planCode,
            ownerType = definition.ownerType,
            ownerId = ownerId,
            status = SubscriptionStatus.ACTIVE,
            features = definition.features,
            limits = definition.limits,
            billingFrequency = null,
            currentPeriodStart = null,
            currentPeriodEnd = null,
            gracePeriodEnd = null,
            purchasedSeats = null,
            upgradePlanCode = definition.upgradePlanCode,
        )
    }

    @Test
    fun `the usage window is the UTC calendar month`()
    {
        val at = Instant.parse("2026-08-10T13:45:12Z")

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), service.calendarMonthStart(at))
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), service.calendarMonthEnd(at))
    }

    @Test
    fun `the usage window respects month length`()
    {
        assertEquals(
            Instant.parse("2026-03-01T00:00:00Z"),
            service.calendarMonthEnd(Instant.parse("2026-02-14T09:00:00Z")),
        )
        assertEquals(
            Instant.parse("2027-01-01T00:00:00Z"),
            service.calendarMonthEnd(Instant.parse("2026-12-31T23:59:59Z")),
        )
    }

    @Test
    fun `a capped individual plan is measured against both Exchange allowances`()
    {
        whenever(exchangeUsageCounter.countCreatedBetween(any(), any(), any())).thenReturn(2)
        whenever(exchangeUsageCounter.countOpen(any())).thenReturn(1)

        val usage = service.measure(
            subscription(PlanCode.FREE, appUserId),
            Instant.parse("2026-08-10T13:45:12Z"),
        )

        assertEquals(2L, usage.newExchangesThisPeriod)
        assertEquals(1L, usage.openExchanges)
        assertNull(usage.activeSeats)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), usage.usagePeriodStart)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), usage.usagePeriodEnd)
    }

    @Test
    fun `an uncapped allowance is never counted`()
    {
        val usage = service.measure(subscription(PlanCode.PERSONAL, appUserId))

        assertNull(usage.newExchangesThisPeriod)
        assertNull(usage.openExchanges)
        assertNull(usage.activeSeats)
        verify(exchangeUsageCounter, never()).countCreatedBetween(any(), any(), any())
        verify(exchangeUsageCounter, never()).countOpen(any())
    }

    @Test
    fun `seat usage is measured only for an organization`()
    {
        whenever(organizationSeatCounter.countActiveSeats(organizationId)).thenReturn(7)

        val organizationUsage = service.measure(subscription(PlanCode.BUSINESS, organizationId))
        assertEquals(7L, organizationUsage.activeSeats)

        val individualUsage = service.measure(subscription(PlanCode.FREE, appUserId))
        assertNull(individualUsage.activeSeats)
        verify(organizationSeatCounter, never()).countActiveSeats(appUserId)
    }
}

