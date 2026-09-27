package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEventDeliveryStanding
import com.docuhyphen.app.api.repository.notification.DomainEventConsumptionRepository
import com.docuhyphen.app.api.repository.notification.DomainEventOutboxRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class DomainEventDeliveryStandingService @Inject constructor(
    private val consumptionRepository: DomainEventConsumptionRepository,
    private val outboxRepository: DomainEventOutboxRepository,
)
{
    fun standings(orderingKeys: Collection<String>): Map<String, DomainEventDeliveryStanding>
    {
        val skipped = consumptionRepository.skippedCounts(orderingKeys)
        val failing = outboxRepository.failingDeliveryCounts(orderingKeys)
        return (skipped.keys + failing.keys).associateWith { key ->
            DomainEventDeliveryStanding(skipped[key] ?: 0, failing[key] ?: 0)
        }
    }
}
