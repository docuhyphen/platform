package com.docuhyphen.app.api.model.notification

data class DomainEventDeliveryStanding(
    val skippedConsumptions: Int,
    val failingDeliveries: Int,
)
