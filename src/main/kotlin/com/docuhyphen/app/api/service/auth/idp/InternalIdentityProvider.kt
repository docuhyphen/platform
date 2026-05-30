package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InternalIdentityProvider : IdentityProviderStrategy
{
    override fun getProviderType(): IdentityProviderType = IdentityProviderType.INTERNAL

    override fun buildAuthorizationUrl(
        state: String,
        nonce: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials?,
        codeChallenge: String?,
        prompt: String?,
    ): String
    {
        throw UnsupportedOperationException("Internal IDP does not support OAuth authorization URLs")
    }

    override fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials?,
        codeVerifier: String?,
    ): OAuthTokenResponse
    {
        throw UnsupportedOperationException("Internal IDP does not support OAuth code exchange")
    }

    override fun validateIdToken(idToken: String, expectedNonce: String, runtimeCredentials: RuntimeIdpCredentials?): OAuthUserInfo
    {
        throw UnsupportedOperationException("Internal IDP does not support external ID token validation")
    }
}
