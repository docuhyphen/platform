package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import java.time.Instant

interface ExchangeUsageCounter
{
    fun countCreatedBetween(
        context: SubscriptionContext,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Long

    fun countOpen(context: SubscriptionContext): Long
}

