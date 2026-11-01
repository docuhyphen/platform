package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.subscription.EffectiveSubscription
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.model.subscription.SubscriptionUsage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

@ApplicationScoped
class SubscriptionUsageService @Inject constructor(
    private val exchangeUsageCounter: ExchangeUsageCounter,
    private val organizationSeatCounter: OrganizationSeatCounter,
)
{
    fun measure(
        subscription: EffectiveSubscription,
        at: Instant = Instant.now(),
    ): SubscriptionUsage
    {
        val periodStart = calendarMonthStart(at)
        val periodEnd = periodStart.plus(daysInMonth(periodStart), ChronoUnit.DAYS)
        val context = SubscriptionContext(subscription.ownerType, subscription.ownerId)

        val newExchanges = if (subscription.limits.hasExchangeCreationCap)
        {
            exchangeUsageCounter.countCreatedBetween(context, periodStart, periodEnd)
        }
        else
        {
            null
        }

        val openExchanges = if (subscription.limits.hasOpenExchangeCap)
        {
            exchangeUsageCounter.countOpen(context)
        }
        else
        {
            null
        }

        val activeSeats = if (subscription.ownerType == SubscriptionOwnerType.ORGANIZATION)
        {
            organizationSeatCounter.countActiveSeats(subscription.ownerId)
        }
        else
        {
            null
        }

        return SubscriptionUsage(
            newExchangesThisPeriod = newExchanges,
            openExchanges = openExchanges,
            activeSeats = activeSeats,
            usagePeriodStart = periodStart,
            usagePeriodEnd = periodEnd,
        )
    }

    fun countNewExchangesThisPeriod(context: SubscriptionContext, at: Instant = Instant.now()): Long
    {
        val periodStart = calendarMonthStart(at)
        val periodEnd = periodStart.plus(daysInMonth(periodStart), ChronoUnit.DAYS)
        return exchangeUsageCounter.countCreatedBetween(context, periodStart, periodEnd)
    }

    fun countOpenExchanges(context: SubscriptionContext): Long
    {
        return exchangeUsageCounter.countOpen(context)
    }

    fun countActiveSeats(organizationId: UUID): Long
    {
        return organizationSeatCounter.countActiveSeats(organizationId)
    }

    fun calendarMonthStart(at: Instant): Instant
    {
        return at.atZone(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
    }

    fun calendarMonthEnd(at: Instant): Instant
    {
        val start = calendarMonthStart(at)
        return start.plus(daysInMonth(start), ChronoUnit.DAYS)
    }

    private fun daysInMonth(monthStart: Instant): Long
    {
        val zoned = monthStart.atZone(ZoneOffset.UTC)
        return zoned.toLocalDate().lengthOfMonth().toLong()
    }
}

