package com.docuhyphen.app.api.service.subscription

import java.time.Instant

/**
 * Supplies the Exchange counts that commercial allowances are measured against.
 *
 * The contract lives with the subscription domain while the implementation lives with the
 * Exchange domain, so allowance evaluation never has to reach into Exchange persistence.
 */
interface ExchangeUsageCounter
{
    /**
     * Exchanges owned by the subject that were created within the given window. The window is
     * half-open so consecutive calendar months never double-count a boundary Exchange.
     */
    fun countCreatedBetween(
        context: SubscriptionContext,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Long

    /**
     * Exchanges owned by the subject that are still open. Drafts count, otherwise the allowance
     * could be sidestepped by leaving Exchanges unsent.
     */
    fun countOpen(context: SubscriptionContext): Long
}

