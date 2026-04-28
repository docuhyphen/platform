package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InternalIdentityProvider : IdentityProviderStrategy
{
    override fun getProviderType(): IdentityProviderType = IdentityProviderType.INTERNAL

    override fun buildAuthorizationUrl(state: String, redirectUri: String): String
    {
        throw UnsupportedOperationException("Internal IDP does not support OAuth authorization URLs")
    }

    override fun exchangeCodeForTokens(code: String, redirectUri: String): OAuthTokenResponse
    {
        throw UnsupportedOperationException("Internal IDP does not support OAuth code exchange")
    }

    override fun validateIdToken(idToken: String): OAuthUserInfo
    {
        throw UnsupportedOperationException("Internal IDP does not support external ID token validation")
    }
}

