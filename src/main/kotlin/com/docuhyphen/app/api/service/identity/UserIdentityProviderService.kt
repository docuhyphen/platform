package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.IdentityProviderLinkDtoMapper
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.IdentityProviderLinkDto
import com.docuhyphen.app.api.service.auth.*
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

class IdentityProviderRequestException(message: String) : RuntimeException(message)

class ReauthenticationRequiredException : RuntimeException("Re-authentication required")

class CurrentPasswordRejectedException : RuntimeException("Current password is incorrect")

@ApplicationScoped
class UserIdentityProviderService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val oauthUserLinkingService: OAuthUserLinkingService,
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val authenticationService: AuthenticationService,
    private val configurationService: ConfigurationService,
    private val oauthStateService: OAuthStateService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationIdpRuntimeCredentialService: OrganizationIdpRuntimeCredentialService,
    private val stepUpAuthService: StepUpAuthService,
    private val appUserCredentialService: AppUserCredentialService,
    private val oauthRedirectUriResolver: OAuthRedirectUriResolver,
)
{
    fun linkedProviders(): List<IdentityProviderLinkDto>
    {
        val appUser = authTokenContext.authToken.appUser!!

        if (!appUser.password.isNullOrBlank())
        {
            oauthUserLinkingService.ensureInternalLink(appUser)
        }

        return oauthUserLinkingService.getLinksForUser(appUser.id).map(IdentityProviderLinkDtoMapper::toDto)
    }

    fun initiateLinking(providerName: String?): String
    {
        if (providerName.isNullOrBlank())
        {
            throw IdentityProviderRequestException("Provider is required")
        }

        val providerType = IdentityProviderType.valueOf(providerName.uppercase())
        val provider = identityProviderRegistry.getProvider(providerType)

        val appUser = authTokenContext.authToken.appUser!!

        val existingLinks = oauthUserLinkingService.getLinksForUser(appUser.id)
        val existingExternal = existingLinks.firstOrNull { it.provider != IdentityProviderType.INTERNAL }

        if (existingExternal != null && existingExternal.provider != providerType)
        {
            throw ExternalProviderAlreadyLinkedException(
                "Already linked to ${existingExternal.provider.displayName}. Unlink it first."
            )
        }

        val redirectUri = oauthRedirectUriResolver.resolve(providerType)

        val orgIdpConfigId = organizationIdentityPolicyService.findActiveProviderConfigIdForEmail(
            appUser.email,
            providerType,
        )
        val runtimeCredentials = organizationIdpRuntimeCredentialService.resolve(providerType, orgIdpConfigId)
        val signedState = oauthStateService.createSignedState(
            flow = "link",
            provider = providerType,
            orgIdpConfigId = orgIdpConfigId,
            linkAppUserId = appUser.id,
        )
        return provider.buildAuthorizationUrl(
            state = signedState.token,
            nonce = signedState.nonce,
            redirectUri = redirectUri,
            runtimeCredentials = runtimeCredentials,
            codeChallenge = signedState.codeChallenge,
        )
    }

    fun unlinkProvider(providerName: String)
    {
        val providerType = IdentityProviderType.valueOf(providerName.uppercase())
        val appUser = authTokenContext.authToken.appUser!!

        if (!stepUpAuthService.isFresh(configurationService.getStepUpMaxAgeSeconds()))
        {
            throw ReauthenticationRequiredException()
        }

        oauthUserLinkingService.unlinkProvider(appUser.id, providerType, appUser)
    }

    fun setupPassword(password: String?, confirmationPassword: String?, currentPassword: String?)
    {
        if (password.isNullOrBlank() || confirmationPassword.isNullOrBlank())
        {
            throw IdentityProviderRequestException("Password and confirmation are required")
        }

        if (password != confirmationPassword)
        {
            throw IdentityProviderRequestException("Passwords do not match")
        }

        if (!authenticationService.isPasswordStrong(password))
        {
            throw IdentityProviderRequestException("Password does not meet requirements")
        }

        val appUser = authTokenContext.authToken.appUser!!

        if (!stepUpAuthService.isFresh(configurationService.getStepUpMaxAgeSeconds()))
        {
            throw ReauthenticationRequiredException()
        }

        if (!appUser.password.isNullOrBlank())
        {
            if (currentPassword.isNullOrBlank() ||
                !authenticationService.validatePassword(currentPassword, appUser.password!!)
            )
            {
                throw CurrentPasswordRejectedException()
            }
        }

        appUserCredentialService.setPassword(appUser.id, password)
    }
}
