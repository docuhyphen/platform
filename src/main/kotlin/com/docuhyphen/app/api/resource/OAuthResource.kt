package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.ExternalProviderAlreadyLinkedException
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OAuthUserLinkingService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.auth.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.SecurityIncidentService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
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
    private val oauthStateService: OAuthStateService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationIdpRuntimeCredentialService: OrganizationIdpRuntimeCredentialService,
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
        @QueryParam("orgIdpConfigId") orgIdpConfigId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
    ): Response
    {
        return try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:oauth:authorize:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitAuthorizePerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_OAUTH_AUTHORIZE,
                    severity = SecurityIncidentSeverity.MEDIUM,
                    requestId = requestId,
                    details = "ip=$clientIp;provider=$providerName",
                )
                authAuditService.emit(
                    action = "OAUTH_AUTHORIZE",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return Response.status(429)
                    .entity(ResponseError("Too many requests. Please try again later."))
                    .build()
            }

            val providerType = IdentityProviderType.valueOf(providerName.uppercase())
            val provider = identityProviderRegistry.getProvider(providerType)

            val redirectUri = getRedirectUri(providerType)
            val orgIdpConfigUuid = orgIdpConfigId
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            val signedState = oauthStateService.createSignedState(flow ?: "signin", providerType, orgIdpConfigUuid)
            val authUrl = provider.buildAuthorizationUrl(
                state = signedState.token,
                nonce = signedState.nonce,
                redirectUri = redirectUri,
                codeChallenge = signedState.codeChallenge,
            )

            authAuditService.emit(
                action = "OAUTH_AUTHORIZE",
                outcome = "SUCCESS",
                requestId = requestId,
            )

            Response.temporaryRedirect(URI.create(authUrl)).build()
        }
        catch (e: Exception)
        {
            logger.error("Error building authorization URL for $providerName", e)
            authAuditService.emit(
                action = "OAUTH_AUTHORIZE",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )

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
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
    ): Response
    {
        return try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:oauth:callback:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitCallbackPerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_OAUTH_CALLBACK,
                    severity = SecurityIncidentSeverity.HIGH,
                    requestId = requestId,
                    details = "ip=$clientIp;provider=$providerName",
                )
                authAuditService.emit(
                    action = "OAUTH_CALLBACK",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return redirectToFrontendError("Too many requests. Please try again later.")
            }

            if (code.isNullOrBlank())
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.OAUTH_CALLBACK_INVALID_REQUEST,
                    severity = SecurityIncidentSeverity.HIGH,
                    requestId = requestId,
                    details = "missing_code;provider=$providerName;ip=$clientIp",
                )
                authAuditService.emit(
                    action = "OAUTH_CALLBACK",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return redirectToFrontendError("Authorization code is missing")
            }

            if (state.isNullOrBlank())
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.OAUTH_STATE_VALIDATION_FAILURE,
                    severity = SecurityIncidentSeverity.HIGH,
                    requestId = requestId,
                    details = "missing_state;provider=$providerName;ip=$clientIp",
                )
                authAuditService.emit(
                    action = "OAUTH_CALLBACK",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return redirectToFrontendError("Invalid OAuth state")
            }

            val providerType = IdentityProviderType.valueOf(providerName.uppercase())
            val provider = identityProviderRegistry.getProvider(providerType)
            val redirectUri = getRedirectUri(providerType)

            val verifiedState = oauthStateService.verifyAndConsumeState(state, providerType)
                ?: run {
                    securityIncidentService.record(
                        incidentType = SecurityIncidentType.OAUTH_STATE_REPLAY_OR_INVALID,
                        severity = SecurityIncidentSeverity.CRITICAL,
                        requestId = requestId,
                        details = "invalid_state;provider=$providerName;ip=$clientIp",
                    )
                    authAuditService.emit(
                        action = "OAUTH_CALLBACK",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    return redirectToFrontendError("Invalid OAuth state")
                }

            val runtimeCredentials = organizationIdpRuntimeCredentialService.resolve(providerType, verifiedState.orgIdpConfigId)

            // Exchange code for tokens
            val oauthResponse = provider.exchangeCodeForTokens(
                code = code,
                redirectUri = redirectUri,
                runtimeCredentials = runtimeCredentials,
                codeVerifier = verifiedState.codeVerifier,
            )
            val userInfo = provider.validateIdToken(oauthResponse.idToken!!, verifiedState.nonce, runtimeCredentials)

            // Enforce per-organization provider allowlist policy when org-specific config exists.
            organizationIdentityPolicyService.assertProviderAllowedForEmail(userInfo.email, providerType)

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
                    .also {
                        authAuditService.emit(
                            action = "OAUTH_CALLBACK",
                            outcome = "SUCCESS",
                            actorId = result.appUser.id,
                            requestId = requestId,
                        )
                    }
            }

            // Issue token triple
            val tokenTriple = tokenIssuanceService.issueTokenTriple(result.appUser)
            val refreshCookie = tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, result.appUser)
            val csrfToken = tokenIssuanceService.generateCsrfToken()
            val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)

            val baseUrl = configurationService.baseUrl
            val callbackUrl = "$baseUrl/oauth/callback" +
                    "?accessToken=${URLEncoder.encode(tokenTriple.accessToken, StandardCharsets.UTF_8)}" +
                    "&idToken=${URLEncoder.encode(tokenTriple.idToken, StandardCharsets.UTF_8)}" +
                    "&isNewUser=${result.isNewUser}"

            Response.temporaryRedirect(URI.create(callbackUrl)).cookie(refreshCookie, csrfCookie).build()
                .also {
                    authAuditService.emit(
                        action = "OAUTH_CALLBACK",
                        outcome = "SUCCESS",
                        actorId = result.appUser.id,
                        requestId = requestId,
                    )
                }
        }
        catch (e: ExternalProviderAlreadyLinkedException)
        {
            logger.warn("External provider conflict during OAuth callback for $providerName: ${e.message}")
            authAuditService.emit(
                action = "OAUTH_CALLBACK",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            redirectToFrontendError(e.message ?: "Provider conflict")
        }
        catch (e: Exception)
        {
            logger.error("Error during OAuth callback for $providerName", e)
            authAuditService.emit(
                action = "OAUTH_CALLBACK",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.OIDC_VALIDATION_FAILED,
                requestId = requestId,
            )
            redirectToFrontendError("OAuth authentication failed")
        }
    }

    @POST
    @Path("/link-confirm")
    fun linkConfirm(
        payload: OAuthLinkConfirmRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
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
            val refreshCookie = tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, appUser)
            val csrfToken = tokenIssuanceService.generateCsrfToken()
            val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)

            Response.ok(OAuthLinkConfirmResponse(tokenTriple.accessToken, tokenTriple.idToken))
                .cookie(refreshCookie, csrfCookie)
                .build()
                .also {
                    authAuditService.emit(
                        action = "OAUTH_LINK_CONFIRM",
                        outcome = "SUCCESS",
                        actorId = appUser.id,
                        requestId = requestId,
                    )
                }
        }
        catch (e: Exception)
        {
            logger.error("Error during OAuth link confirmation", e)
            authAuditService.emit(
                action = "OAUTH_LINK_CONFIRM",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
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

    private fun getClientIpAddress(request: io.vertx.core.http.HttpServerRequest): String
    {
        var ipAddress = request.getHeader("X-Forwarded-For")

        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true))
        {
            ipAddress = request.getHeader("Proxy-Client-IP")
        }

        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true))
        {
            ipAddress = request.getHeader("X-Real-IP")
        }

        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true))
        {
            ipAddress = request.remoteAddress()?.host() ?: "0.0.0.0"
        }

        if (ipAddress.contains(","))
        {
            ipAddress = ipAddress.split(",")[0].trim()
        }

        return ipAddress
    }
}



