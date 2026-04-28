package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.ExternalProviderAlreadyLinkedException
import com.docuhyphen.app.api.service.auth.OAuthUserLinkingService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Path("/auth/oauth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class OAuthResource @Inject constructor(
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val oauthUserLinkingService: OAuthUserLinkingService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val authenticationService: AuthenticationService,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OAuthResource::class.java)
    }

    @GET
    @Path("/{provider}/authorize")
    fun authorize(
        @PathParam("provider") providerName: String,
        @QueryParam("flow") flow: String?,
    ): Response
    {
        return try
        {
            val providerType = IdentityProviderType.valueOf(providerName.uppercase())
            val provider = identityProviderRegistry.getProvider(providerType)

            val redirectUri = getRedirectUri(providerType)
            val state = "flow=${flow ?: "signin"}"
            val authUrl = provider.buildAuthorizationUrl(state, redirectUri)

            Response.temporaryRedirect(URI.create(authUrl)).build()
        }
        catch (e: Exception)
        {
            logger.error("Error building authorization URL for $providerName", e)

            Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Invalid provider: $providerName"))
                .build()
        }
    }

    @GET
    @Path("/{provider}/callback")
    fun callback(
        @PathParam("provider") providerName: String,
        @QueryParam("code") code: String?,
        @QueryParam("state") state: String?,
    ): Response
    {
        return try
        {
            if (code.isNullOrBlank())
            {
                return redirectToFrontendError("Authorization code is missing")
            }

            val providerType = IdentityProviderType.valueOf(providerName.uppercase())
            val provider = identityProviderRegistry.getProvider(providerType)
            val redirectUri = getRedirectUri(providerType)

            // Exchange code for tokens
            val oauthResponse = provider.exchangeCodeForTokens(code, redirectUri)
            val userInfo = provider.validateIdToken(oauthResponse.idToken!!)

            // Link or create user
            val result = oauthUserLinkingService.linkOrCreateUser(providerType, userInfo)

            if (result.requiresLinkConfirmation)
            {
                // Redirect to frontend link-confirm page
                val baseUrl = configurationService.baseUrl
                val params = "provider=${providerType.name}" +
                        "&email=${URLEncoder.encode(userInfo.email, StandardCharsets.UTF_8)}" +
                        "&linkToken=${URLEncoder.encode(result.linkToken!!, StandardCharsets.UTF_8)}"
                return Response.temporaryRedirect(
                    URI.create("$baseUrl/oauth/link-confirm?$params")
                ).build()
            }

            // Issue token triple
            val tokenTriple = tokenIssuanceService.issueTokenTriple(result.appUser)
            val cookie = tokenIssuanceService.buildRefreshTokenCookie(tokenTriple.refreshToken)

            val baseUrl = configurationService.baseUrl
            val callbackUrl = "$baseUrl/oauth/callback" +
                    "?accessToken=${URLEncoder.encode(tokenTriple.accessToken, StandardCharsets.UTF_8)}" +
                    "&idToken=${URLEncoder.encode(tokenTriple.idToken, StandardCharsets.UTF_8)}" +
                    "&isNewUser=${result.isNewUser}"

            Response.temporaryRedirect(URI.create(callbackUrl)).cookie(cookie).build()
        }
        catch (e: ExternalProviderAlreadyLinkedException)
        {
            logger.warn("External provider conflict during OAuth callback for $providerName: ${e.message}")
            redirectToFrontendError(e.message ?: "Provider conflict")
        }
        catch (e: Exception)
        {
            logger.error("Error during OAuth callback for $providerName", e)
            redirectToFrontendError("OAuth authentication failed")
        }
    }

    @POST
    @Path("/link-confirm")
    fun linkConfirm(
        payload: OAuthLinkConfirmRequest,
    ): Response
    {
        return try
        {
            if (payload.linkToken.isNullOrBlank() || payload.password.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Link token and password are required"))
                    .build()
            }

            val claims = authenticationService.parseTokenClaims(payload.linkToken!!)
                ?: return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid or expired link token"))
                    .build()

            val tokenType = claims["token_type"] as? String
            if (tokenType != "LINK")
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid link token"))
                    .build()
            }

            val email = claims.subject
            val providerName = claims["provider"] as? String
                ?: return Response.status(Response.Status.BAD_REQUEST).entity(ResponseError("Invalid link token")).build()
            val externalSubjectId = claims["externalSubjectId"] as? String
                ?: return Response.status(Response.Status.BAD_REQUEST).entity(ResponseError("Invalid link token")).build()

            val appUser = appUserService.findByEmail(email)
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseError("User not found"))
                    .build()

            // Validate password
            if (appUser.password == null || !authenticationService.validatePassword(payload.password!!, appUser.password!!))
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid password"))
                    .build()
            }

            // Create the link
            val providerType = IdentityProviderType.valueOf(providerName)
            oauthUserLinkingService.createLink(appUser, providerType, externalSubjectId, email)

            // Issue token triple
            val tokenTriple = tokenIssuanceService.issueTokenTriple(appUser)
            val cookie = tokenIssuanceService.buildRefreshTokenCookie(tokenTriple.refreshToken)

            Response.ok(OAuthLinkConfirmResponse(tokenTriple.accessToken, tokenTriple.idToken))
                .cookie(cookie)
                .build()
        }
        catch (e: Exception)
        {
            logger.error("Error during OAuth link confirmation", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An error occurred during account linking"))
                .build()
        }
    }

    private fun getRedirectUri(providerType: IdentityProviderType): String
    {
        return when (providerType)
        {
            IdentityProviderType.MICROSOFT -> configurationService.microsoftOAuthRedirectUri
            IdentityProviderType.GOOGLE -> configurationService.googleOAuthRedirectUri
            else -> throw IllegalArgumentException("No redirect URI configured for $providerType")
        }
    }

    private fun redirectToFrontendError(message: String): Response
    {
        val baseUrl = configurationService.baseUrl
        val encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8)
        return Response.temporaryRedirect(
            URI.create("$baseUrl/sign-in?error=$encodedMsg")
        ).build()
    }
}



