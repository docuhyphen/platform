package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant

/**
 * Counts Exchanges for commercial allowance checks.
 *
 * Ownership is read from the persisted owner columns rather than the caller's selected
 * organization, so an Exchange always counts against whoever actually pays for it. Deleted
 * Exchanges never count.
 */
@ApplicationScoped
class ExchangeUsageCounterService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
) : ExchangeUsageCounter
{
    companion object
    {
        /** An Exchange occupies open capacity until it is completed, rejected, or rescinded. */
        private val OPEN_STATUSES = listOf(ExchangeStatus.INITIATED, ExchangeStatus.ACCEPTED_STARTED)
    }

    override fun countCreatedBetween(
        context: SubscriptionContext,
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Long
    {
        val from = Timestamp.from(fromInclusive)
        val to = Timestamp.from(toExclusive)

        return when (context.ownerType)
        {
            SubscriptionOwnerType.USER ->
                exchangeRepository.countCreatedByOwnerUserBetween(context.ownerId, from, to)

            SubscriptionOwnerType.ORGANIZATION ->
                exchangeRepository.countCreatedByOwnerOrganizationBetween(context.ownerId, from, to)
        }
    }

    override fun countOpen(context: SubscriptionContext): Long
    {
        return when (context.ownerType)
        {
            SubscriptionOwnerType.USER ->
                exchangeRepository.countByOwnerUserAndStatuses(context.ownerId, OPEN_STATUSES)

            SubscriptionOwnerType.ORGANIZATION ->
                exchangeRepository.countByOwnerOrganizationAndStatuses(context.ownerId, OPEN_STATUSES)
        }
    }
}

