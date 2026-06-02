package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import java.util.UUID

/**
 * One concrete delivery to attempt: send [event] to [recipientUserId] via [channel].
 * Produced by [NotificationRuleEngine.resolveDeliveries] and consumed by [DeliveryDispatcher].
 */
data class DeliveryTask(
    val event: DomainEvent,
    val recipientUserId: UUID,
    val channel: NotificationChannelType,
)

