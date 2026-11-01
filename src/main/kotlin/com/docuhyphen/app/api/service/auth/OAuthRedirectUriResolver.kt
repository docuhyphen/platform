package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class OAuthRedirectUriResolver @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    fun resolve(providerType: IdentityProviderType): String
    {
        return when (providerType)
        {
            IdentityProviderType.MICROSOFT -> configurationService.microsoftOAuthRedirectUri
            IdentityProviderType.GOOGLE -> configurationService.googleOAuthRedirectUri
            else -> throw IllegalArgumentException("No redirect URI configured for $providerType")
        }
    }
}
