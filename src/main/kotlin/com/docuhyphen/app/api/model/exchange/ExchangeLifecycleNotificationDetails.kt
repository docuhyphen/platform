package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.service.notification.UserNotificationPreference

internal data class ExchangeLifecycleNotificationDetails(
    val preference: UserNotificationPreference,
    val type: String,
    val title: String,
)
