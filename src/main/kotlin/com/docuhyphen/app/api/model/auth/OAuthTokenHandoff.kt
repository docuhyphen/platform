package com.docuhyphen.app.api.model.auth

data class OAuthTokenHandoff(
    val accessToken: String,
    val idToken: String,
    val isNewUser: Boolean,
)
