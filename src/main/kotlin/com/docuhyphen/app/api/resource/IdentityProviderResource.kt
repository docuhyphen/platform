package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.ExternalProviderAlreadyLinkedException
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OAuthUserLinkingService
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
                    .entity(ResponseError("Already linked to ${existingExternal.provider}. Unlink it first."))
                    .build()
            }

            val redirectUri = when (providerType)
            {
                IdentityProviderType.MICROSOFT -> configurationService.microsoftOAuthRedirectUri
                IdentityProviderType.GOOGLE -> configurationService.googleOAuthRedirectUri
                else -> throw IllegalArgumentException("Linking not supported for $providerType")
            }

            val signedState = oauthStateService.createSignedState("link", providerType)
            val authUrl = provider.buildAuthorizationUrl(signedState.token, signedState.nonce, redirectUri)

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

            val salt = authenticationService.generatePasswordSalt()
            appUser.password = authenticationService.hashPassword(payload.password!!, salt)
            appUser.passwordSalt = salt

            // Create INTERNAL link if not exists
            oauthUserLinkingService.createLink(
                appUser, IdentityProviderType.INTERNAL, appUser.id.toString(), appUser.email
            )

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

