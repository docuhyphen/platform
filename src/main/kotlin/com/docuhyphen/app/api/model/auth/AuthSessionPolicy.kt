package com.docuhyphen.app.api.model.auth

data class AuthSessionPolicy(
    val accessTokenExpiryMinutes: Long,
    val refreshTokenExpiryMinutes: Long,
    val maxSessionDurationHours: Long,
    val idleTimeoutMinutes: Long,
)
