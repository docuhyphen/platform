package com.docuhyphen.app.api.model.subscription

import java.time.Instant

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

