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

data class RuntimeIdpCredentials(
    val clientId: String,
    val clientSecret: String,
    val tenantId: String? = null,
    val scopes: String? = null,
    val oidcIssuer: String? = null,
    val allowedAudiences: Set<String> = emptySet(),
    val allowedAlgs: Set<String> = emptySet(),
    val requiredClaims: Set<String> = emptySet(),
)

interface IdentityProviderStrategy
{
    fun getProviderType(): IdentityProviderType

    fun buildAuthorizationUrl(
        state: String,
        nonce: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials? = null,
        codeChallenge: String? = null,
        prompt: String? = null,
    ): String

    fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials? = null,
        codeVerifier: String? = null,
    ): OAuthTokenResponse

    fun validateIdToken(idToken: String, expectedNonce: String, runtimeCredentials: RuntimeIdpCredentials? = null): OAuthUserInfo
}

