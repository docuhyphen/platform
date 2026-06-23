package com.docuhyphen.app.api.service.communication

import kotlinx.serialization.Serializable

@Serializable
data class RenderedCommunication(
    val subject: String,
    val body: String,
    val metadata: Map<String, String> = emptyMap(),
)
