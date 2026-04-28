package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType

data class OAuthTokenResponse(
    val idToken: String?,
    val accessToken: String?,
    val email: String,
    val subjectId: String,
    val name: String?,
)

data class OAuthUserInfo(
    val email: String,
    val subjectId: String,
    val firstName: String?,
    val lastName: String?,
)

interface IdentityProviderStrategy
{
    fun getProviderType(): IdentityProviderType

    fun buildAuthorizationUrl(state: String, redirectUri: String): String

    fun exchangeCodeForTokens(code: String, redirectUri: String): OAuthTokenResponse

    fun validateIdToken(idToken: String): OAuthUserInfo
}

