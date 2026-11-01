package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.auth.idp.OAuthTokenResponse
import com.docuhyphen.app.api.model.auth.idp.OAuthUserInfo
import com.docuhyphen.app.api.model.auth.idp.RuntimeIdpCredentials
import com.docuhyphen.app.api.model.entity.IdentityProviderType

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

    fun validateIdToken(
        idToken: String,
        expectedNonce: String,
        runtimeCredentials: RuntimeIdpCredentials? = null
    ): OAuthUserInfo
}
