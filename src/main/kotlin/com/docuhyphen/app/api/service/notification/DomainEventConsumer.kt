package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult

interface DomainEventConsumer
{
    val consumerKey: String

    val routingOrder: Int
        get() = 0

    fun handles(event: DomainEvent): Boolean

    fun consume(event: DomainEvent): DomainEventConsumptionResult?
}
