package com.docuhyphen.app.api.model.auth

data class TokenTriple(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String,
    val refreshTokenJti: String,
)
