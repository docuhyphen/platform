package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.service.notification.ChannelSendResult
import com.docuhyphen.app.api.service.notification.DeliveryTask

/**
 * Strategy interface implemented by one bean per [NotificationChannelType]. CDI injects
 * all implementations into the [com.docuhyphen.app.api.service.notification.DeliveryDispatcher]
 * via `Instance<NotificationChannel>`, and the dispatcher picks by [type].
 *
 * Channels MUST NOT throw, return [ChannelSendResult.Failed] instead so the dispatcher
 * can record the outcome and walk the fallback chain.
 */
interface NotificationChannel
{
    val type: NotificationChannelType
    fun send(task: DeliveryTask): ChannelSendResult
}

