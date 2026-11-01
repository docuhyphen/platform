package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class ExchangeUsageCounterService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
) : ExchangeUsageCounter
{
    companion object
    {
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

