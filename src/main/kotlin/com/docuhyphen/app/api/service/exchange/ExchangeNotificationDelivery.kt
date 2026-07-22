package com.docuhyphen.app.api.service.exchange

import java.util.UUID

data class ExchangeEmailDelivery(
    val to: String,
    val subject: String,
    val body: String,
    val preferenceAppUserId: UUID? = null,
)

data class ExchangeInAppDelivery(
    val appUserId: UUID,
    val type: String,
    val title: String,
    val message: String,
    val data: Map<String, String>,
)
