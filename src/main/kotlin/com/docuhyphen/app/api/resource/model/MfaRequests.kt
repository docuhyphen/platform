package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticatorEnrollmentRequest(val provider: String? = null)

@Serializable
data class AuthenticatorEnrollmentVerificationRequest(
    val code: String? = null,
    val emailFallbackEnabled: Boolean = false,
)

@Serializable
data class MfaConfigurationUpdateRequest(val emailFallbackEnabled: Boolean)

@Serializable
data class EmailFallbackChallengeRequest(
    val email: String? = null,
)
