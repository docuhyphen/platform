package com.docuhyphen.app.api.model.auth

data class TokenRefreshCommand(
    val refreshToken: String?,
    val csrfCookie: String?,
    val csrfHeader: String?,
    val originHeader: String?,
    val refererHeader: String?,
    val userAgent: String?,
    val requestId: String?,
    val clientIp: String,
)

data class SignOutCommand(
    val outOfAllDevices: Boolean,
    val csrfCookie: String?,
    val csrfHeader: String?,
    val originHeader: String?,
    val refererHeader: String?,
    val requestId: String?,
    val clientIp: String,
)
