package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class StepUpInitiateRequest(val returnTo: String? = null, val action: String? = null)

@Serializable
data class StepUpCompleteRequest(
    val mfaSessionId: String? = null,
    val otp: String? = null,
)

@Serializable
data class StepUpOtpRegenerationRequest(
    val mfaSessionId: String? = null,
)
