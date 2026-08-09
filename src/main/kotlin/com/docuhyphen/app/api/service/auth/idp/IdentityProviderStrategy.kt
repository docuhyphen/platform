package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType

/**
 * Raw token endpoint result.
 *
 * `email`, `subjectId`, and `name` are transport-level conveniences only. Identity decisions
 * must be made from [IdentityProviderStrategy.validateIdToken], which is the only path that
 * checks the nonce, the audience, and the organization's runtime credentials.
 */
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
    val legacySubjectId: String? = null,
    /**
     * True only when the provider asserted that the mailbox domain belongs to the directory
     * that signed the token. Never assume an unverified address identifies its real owner:
     * it must not silently mark a new account as email-verified, and it must not be used to
     * match an existing account without a further proof of possession.
     */
    val emailVerified: Boolean = false,
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
    /**
     * Google Workspace domain to pin the `hd` claim against. Distinct from [tenantId] so a
     * Microsoft directory id and a Google workspace domain can never be confused for each other.
     */
    val workspaceDomain: String? = null,
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

