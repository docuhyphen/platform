package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class NotificationFanOutConsumer @Inject constructor(
    private val ruleEngine: NotificationRuleEngine,
    private val dispatcher: DeliveryDispatcher,
) : DomainEventConsumer
{
    override val consumerKey: String = CONSUMER_KEY

    override val routingOrder: Int = LAST

    override fun handles(event: DomainEvent): Boolean = true

    override fun consume(event: DomainEvent): DomainEventConsumptionResult?
    {
        val tasks = ruleEngine.resolveDeliveries(event)
        if (tasks.isEmpty()) return null
        dispatcher.dispatchAll(tasks)
        return DomainEventConsumptionResult.applied("${tasks.size} deliveries")
    }

    companion object
    {
        const val CONSUMER_KEY = "notification-fan-out"
        private const val LAST = 1000
    }
}
