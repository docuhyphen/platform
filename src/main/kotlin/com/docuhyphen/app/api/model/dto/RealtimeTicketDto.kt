package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class RealtimeTicketDto(
    val ticket: String,
    val expiresInSeconds: Long,
)
