package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class MfaConfigurationDto(
    val method: String,
    val authenticatorConfigured: Boolean,
    val emailFallbackEnabled: Boolean,
)

@Serializable
data class AuthenticatorEnrollmentDto(
    val id: String,
    val provider: String,
    val secret: String,
    val otpauthUri: String,
    val expiresAt: String,
)
