package com.docuhyphen.app.api.resource.identity
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.AppUserCredentialService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.ExternalProviderAlreadyLinkedException
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OAuthUserLinkingService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.auth.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/auth/identity-providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class IdentityProviderResource @Inject constructor(
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
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(IdentityProviderResource::class.java)
    }

    @GET
    fun getLinkedProviders(): Response
    {
        return try
        {
            val appUser = authTokenContext.authToken.appUser!!

            // Lazily backfill INTERNAL link for existing users who signed up before
            // the IDP-link system was introduced (they have a password but no link row).
            if (!appUser.password.isNullOrBlank())
            {
                oauthUserLinkingService.ensureInternalLink(appUser)
            }

            val links = oauthUserLinkingService.getLinksForUser(appUser.id)

            val dtos = links.map { link ->
                IdentityProviderLinkDto(
                    provider = link.provider.name,
                    externalEmail = link.externalEmail,
                    createdDate = link.createdDate.toString(),
                )
            }

            Response.ok(object : GenericEntity<List<IdentityProviderLinkDto>>(dtos) {}).build()
        }
        catch (e: Exception)
        {
            logger.error("Error fetching identity providers", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to fetch identity providers"))
                .build()
        }
    }


    @POST
    @Path("/link/initiate")
    fun initiateLinking(
        payload: LinkProviderInitiateRequest,
    ): Response
    {
        return try
        {
            if (payload.provider.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Provider is required"))
                    .build()
            }

            val providerType = IdentityProviderType.valueOf(payload.provider!!.uppercase())
            val provider = identityProviderRegistry.getProvider(providerType)

            val appUser = authTokenContext.authToken.appUser!!

            // Enforce single external IDP: check if user already has a different external provider
            val existingLinks = oauthUserLinkingService.getLinksForUser(appUser.id)
            val existingExternal = existingLinks.firstOrNull { it.provider != IdentityProviderType.INTERNAL }

            if (existingExternal != null && existingExternal.provider != providerType)
            {
                return Response.status(Response.Status.CONFLICT)
                    .entity(ResponseError(
                        "Already linked to ${existingExternal.provider.displayName}. Unlink it first."
                    ))
                    .build()
            }

            val redirectUri = when (providerType)
            {
                IdentityProviderType.MICROSOFT -> configurationService.microsoftOAuthRedirectUri
                IdentityProviderType.GOOGLE -> configurationService.googleOAuthRedirectUri
                else -> throw IllegalArgumentException("Linking not supported for $providerType")
            }

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
            val authUrl = provider.buildAuthorizationUrl(
                state = signedState.token,
                nonce = signedState.nonce,
                redirectUri = redirectUri,
                runtimeCredentials = runtimeCredentials,
                codeChallenge = signedState.codeChallenge,
            )

            Response.ok(LinkProviderInitiateResponse(authUrl)).build()
        }
        catch (e: ExternalProviderAlreadyLinkedException)
        {
            Response.status(Response.Status.CONFLICT)
                .entity(ResponseError(e.message))
                .build()
        }
        catch (e: Exception)
        {
            logger.error("Error initiating provider linking", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to initiate provider linking"))
                .build()
        }
    }

    @DELETE
    @Path("/{provider}")
    fun unlinkProvider(
        @PathParam("provider") providerName: String,
    ): Response
    {
        return try
        {
            val providerType = IdentityProviderType.valueOf(providerName.uppercase())
            val appUser = authTokenContext.authToken.appUser!!

            // Removing a sign-in method is an account-takeover primitive on a stolen token:
            // it can strip the victim's external provider, or clear their password entirely.
            // Require proof of a recent real authentication challenge first.
            if (!stepUpAuthService.isFresh(configurationService.getStepUpMaxAgeSeconds()))
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Re-authentication required", reasonCode = "STEP_UP_REQUIRED"))
                    .build()
            }

            oauthUserLinkingService.unlinkProvider(appUser.id, providerType, appUser)

            Response.ok().build()
        }
        catch (e: IllegalStateException)
        {
            Response.status(Response.Status.CONFLICT)
                .entity(ResponseError(e.message))
                .build()
        }
        catch (e: Exception)
        {
            logger.error("Error unlinking provider $providerName", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to unlink provider"))
                .build()
        }
    }

    @POST
    @Path("/internal/setup-password")
    fun setupPassword(
        payload: SetupPasswordRequest,
    ): Response
    {
        return try
        {
            if (payload.password.isNullOrBlank() || payload.confirmationPassword.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Password and confirmation are required"))
                    .build()
            }

            if (payload.password != payload.confirmationPassword)
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Passwords do not match"))
                    .build()
            }

            if (!authenticationService.isPasswordStrong(payload.password!!))
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Password does not meet requirements"))
                    .build()
            }

            val appUser = authTokenContext.authToken.appUser!!

            // Setting a password creates a durable credential the account owner may not know
            // about, so a stolen access token alone must not be enough to do it.
            if (!stepUpAuthService.isFresh(configurationService.getStepUpMaxAgeSeconds()))
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Re-authentication required", reasonCode = "STEP_UP_REQUIRED"))
                    .build()
            }

            // An account that already has a password is changing it, which requires proving
            // knowledge of the current one rather than merely holding a session.
            if (!appUser.password.isNullOrBlank())
            {
                val currentPassword = payload.currentPassword
                if (currentPassword.isNullOrBlank() ||
                    !authenticationService.validatePassword(currentPassword, appUser.password!!))
                {
                    return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Current password is incorrect"))
                        .build()
                }
            }

            appUserCredentialService.setPassword(appUser.id, payload.password!!)

            Response.ok().build()
        }
        catch (e: Exception)
        {
            logger.error("Error setting up password", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to set up password"))
                .build()
        }
    }
}

