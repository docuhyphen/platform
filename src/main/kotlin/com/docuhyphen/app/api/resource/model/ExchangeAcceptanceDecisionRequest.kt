package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.ExchangeAcceptanceDecision
import kotlinx.serialization.Serializable

@Serializable
data class ExchangeAcceptanceDecisionRequest(
    val decision: ExchangeAcceptanceDecision,
    val reason: String? = null,
)
