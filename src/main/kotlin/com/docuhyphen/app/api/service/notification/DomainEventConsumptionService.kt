package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult
import com.docuhyphen.app.api.repository.notification.DomainEventConsumptionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@ApplicationScoped
class DomainEventConsumptionService @Inject constructor(
    private val repository: DomainEventConsumptionRepository,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun consumeOnce(consumer: DomainEventConsumer, event: DomainEvent): DomainEventConsumptionResult?
    {
        val eventId = event.eventUuid()
        if (repository.outcomeOf(consumer.consumerKey, eventId) != null) return null
        val result = consumer.consume(event) ?: return null
        repository.record(consumer.consumerKey, eventId, result.outcome, result.detail)
        return result
    }

    fun outcomeOf(consumerKey: String, event: DomainEvent): DomainEventConsumptionOutcome? =
        repository.outcomeOf(consumerKey, event.eventUuid())
}
