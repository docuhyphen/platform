package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestExchangeCompletionRefusalDto(
    val errorMessage: String,
    val reasonCode: String,
    val informationRequestIds: List<String>,
)
