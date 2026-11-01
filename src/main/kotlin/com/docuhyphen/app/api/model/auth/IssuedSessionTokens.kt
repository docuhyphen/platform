package com.docuhyphen.app.api.model.auth

import jakarta.ws.rs.core.NewCookie

data class SessionCookies(
    val refreshCookie: NewCookie,
    val csrfCookie: NewCookie,
)

data class IssuedSessionTokens(
    val accessToken: String,
    val idToken: String,
    val cookies: SessionCookies,
)
