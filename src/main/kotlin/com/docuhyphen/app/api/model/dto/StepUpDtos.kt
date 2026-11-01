package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class StepUpInitiateResponse(
    val method: String,
    val message: String,
    val mfaSessionId: String? = null,
    val provider: String? = null,
    val authorizeUrl: String? = null,
    val mfaType: String? = null,
    val emailFallbackEnabled: Boolean = false,
)

@Serializable
data class StepUpResponse(
    val fresh: Boolean,
    val message: String,
    val mfaType: String? = null,
    val emailFallbackEnabled: Boolean = false,
)
