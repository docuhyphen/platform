package com.docuhyphen.app.api.model.notification

import kotlinx.serialization.Serializable

@Serializable
internal data class NotificationPredicateExpr(
    val field: String,
    @kotlinx.serialization.SerialName("equals") val equals_: String,
)
