package com.docuhyphen.app.api.service.notification.channels

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.notification.ChannelSendResult
import com.docuhyphen.app.api.model.notification.DeliveryTask

interface NotificationChannel
{
    val type: NotificationChannelType
    fun send(task: DeliveryTask): ChannelSendResult
}

