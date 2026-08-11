package com.docuhyphen.app.api.service.subscription

import java.time.Instant

/**
 * How much of each capped allowance the paying subject is currently consuming.
 *
 * A null field means the resolved plan does not cap that allowance, so it was never counted.
 * [usagePeriodStart] and [usagePeriodEnd] describe the calendar month the Exchange creation
 * count belongs to, which is also the point at which that count resets.
 */
data class SubscriptionUsage(
    val newExchangesThisPeriod: Long? = null,
    val openExchanges: Long? = null,
    val activeSeats: Long? = null,
    val usagePeriodStart: Instant? = null,
    val usagePeriodEnd: Instant? = null,
)
{
    companion object
    {
        val NONE = SubscriptionUsage()
    }
}

