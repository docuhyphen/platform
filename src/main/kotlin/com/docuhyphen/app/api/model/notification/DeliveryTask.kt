package com.docuhyphen.app.api.model.notification

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import java.util.*

data class DeliveryTask(
    val event: DomainEvent,
    val recipientUserId: UUID,
    val channel: NotificationChannelType,
)

