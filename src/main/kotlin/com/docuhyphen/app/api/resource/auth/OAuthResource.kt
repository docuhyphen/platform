package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.auth.ExternalProviderAlreadyLinkedException
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OAuthUserLinkingService
import com.docuhyphen.app.api.service.auth.OAuthTokenHandoffService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.auth.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.SecurityIncidentService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.auth.UnverifiedExternalEmailException
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
    private val stepUpAuthService: com.docuhyphen.app.api.service.auth.StepUpAuthService,
    private val oauthTokenHandoffService: OAuthTokenHandoffService,
    private val clientIpResolver: ClientIpResolver,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OAuthResource::class.java)

        /**
         * Stable, non-descriptive codes handed to the sign-in page. Provider-specific or
         * account-specific detail is deliberately withheld from the redirect URL so the flow
         * cannot be used to probe which accounts exist or which provider they use.
         */
        private const val ERROR_INVALID_REQUEST = "OAUTH_INVALID_REQUEST"
        private const val ERROR_INVALID_STATE = "OAUTH_INVALID_STATE"
        private const val ERROR_RATE_LIMITED = "OAUTH_RATE_LIMITED"
        private const val ERROR_PROVIDER_CONFLICT = "OAUTH_PROVIDER_CONFLICT"
        private const val ERROR_EMAIL_NOT_VERIFIED = "OAUTH_EMAIL_NOT_VERIFIED"
        private const val ERROR_LINK_CONTEXT_MISSING = "OAUTH_LINK_CONTEXT_MISSING"
        private const val ERROR_ACCOUNT_NOT_ELIGIBLE = "OAUTH_ACCOUNT_NOT_ELIGIBLE"
        private const val ERROR_STEP_UP_MISMATCH = "OAUTH_STEP_UP_MISMATCH"
        private const val ERROR_FAILED = "OAUTH_FAILED"
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
            val clientIp = clientIpResolver.resolve(request)
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
            val runtimeCredentials = organizationIdpRuntimeCredentialService.resolve(providerType, orgIdpConfigUuid)
            val authUrl = provider.buildAuthorizationUrl(
                state = signedState.token,
                nonce = signedState.nonce,
                redirectUri = redirectUri,
                runtimeCredentials = runtimeCredentials,
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
            val clientIp = clientIpResolver.resolve(request)
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
                return redirectToFrontendError(ERROR_RATE_LIMITED)
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
                return redirectToFrontendError(ERROR_INVALID_REQUEST)
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
                return redirectToFrontendError(ERROR_INVALID_STATE)
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
                    return redirectToFrontendError(ERROR_INVALID_STATE)
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

            if (verifiedState.flow.equals("link", ignoreCase = true))
            {
                val linkingUserId = verifiedState.linkAppUserId
                    ?: return redirectToFrontendError(ERROR_LINK_CONTEXT_MISSING)
                val linkingUser = appUserService.getById(linkingUserId)
                    ?: return redirectToFrontendError(ERROR_LINK_CONTEXT_MISSING)
                if (!linkingUser.isActive || linkingUser.deprovisionedAt != null || linkingUser.isTemporary)
                {
                    return redirectToFrontendError(ERROR_ACCOUNT_NOT_ELIGIBLE)
                }

                oauthUserLinkingService.createLink(
                    appUser = linkingUser,
                    provider = providerType,
                    externalSubjectId = userInfo.subjectId,
                    externalEmail = userInfo.email,
                )
                val tokenTriple = tokenIssuanceService.issueTokenTriple(
                    linkingUser,
                    userAgent = request.getHeader("User-Agent"),
                    ipAddress = clientIp,
                )
                val handoffCode = oauthTokenHandoffService.create(
                    accessToken = tokenTriple.accessToken,
                    idToken = tokenTriple.idToken,
                    isNewUser = false,
                )
                val refreshCookie = tokenIssuanceService.buildRefreshTokenCookieWithPolicy(
                    tokenTriple.refreshToken,
                    linkingUser,
                )
                val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(
                    tokenIssuanceService.generateCsrfToken()
                )
                val callbackUrl = "${configurationService.baseUrl}/oauth/callback" +
                    "?code=${URLEncoder.encode(handoffCode, StandardCharsets.UTF_8)}"
                return Response.temporaryRedirect(URI.create(callbackUrl))
                    .cookie(refreshCookie, csrfCookie)
                    .build()
            }

            if (verifiedState.flow.equals("stepup", ignoreCase = true))
            {
                val sessionId = verifiedState.stepUpSessionId
                    ?: return redirectToFrontendError(ERROR_STEP_UP_MISMATCH)
                val actorId = verifiedState.stepUpAppUserId
                    ?: return redirectToFrontendError(ERROR_STEP_UP_MISMATCH)
                val expectedSubject = verifiedState.stepUpExpectedSubjectId
                    ?: return redirectToFrontendError(ERROR_STEP_UP_MISMATCH)

                if (userInfo.subjectId != expectedSubject && userInfo.legacySubjectId != expectedSubject)
                {
                    authAuditService.emit(
                        action = "STEP_UP_OAUTH_CALLBACK",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.STEP_UP_REQUIRED,
                        actorId = actorId,
                        requestId = requestId,
                        reason = "Step-up external subject mismatch",
                    )
                    return redirectToFrontendError(ERROR_STEP_UP_MISMATCH)
                }

                stepUpAuthService.markFresh(sessionId)
                authAuditService.emit(
                    action = "STEP_UP_OAUTH_CALLBACK",
                    outcome = "SUCCESS",
                    actorId = actorId,
                    requestId = requestId,
                )
                return redirectToStepUpReturn(verifiedState.stepUpReturnTo)
            }

            // Enforce per-organization provider allowlist policy when org-specific config exists.
            val trustedOrganization = organizationIdentityPolicyService.resolveTrustedOrganizationForOAuth(
                email = userInfo.email,
                provider = providerType,
                orgIdpConfigId = verifiedState.orgIdpConfigId,
            )

            // Link or create user
            val result = oauthUserLinkingService.linkOrCreateUser(providerType, userInfo, trustedOrganization)

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
            val tokenTriple = tokenIssuanceService.issueTokenTriple(result.appUser,
                userAgent = request.getHeader("User-Agent"),
                ipAddress = clientIp,
            )
            val refreshCookie = tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, result.appUser)
            val csrfToken = tokenIssuanceService.generateCsrfToken()
            val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)

            val baseUrl = configurationService.baseUrl
            val handoffCode = oauthTokenHandoffService.create(
                accessToken = tokenTriple.accessToken,
                idToken = tokenTriple.idToken,
                isNewUser = result.isNewUser,
            )
            val callbackUrl = "$baseUrl/oauth/callback" +
                "?code=${URLEncoder.encode(handoffCode, StandardCharsets.UTF_8)}"

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
            redirectToFrontendError(ERROR_PROVIDER_CONFLICT)
        }
        catch (e: UnverifiedExternalEmailException)
        {
            logger.warn("Unverified external email during OAuth callback for {}", providerName)
            securityIncidentService.record(
                incidentType = SecurityIncidentType.OAUTH_CALLBACK_INVALID_REQUEST,
                severity = SecurityIncidentSeverity.HIGH,
                requestId = requestId,
                details = "unverified_external_email;provider=$providerName",
            )
            authAuditService.emit(
                action = "OAUTH_CALLBACK",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.OIDC_VALIDATION_FAILED,
                requestId = requestId,
            )
            redirectToFrontendError(ERROR_EMAIL_NOT_VERIFIED)
        }
        catch (e: SubscriptionDenialException)
        {
            logger.error("Subscription denied during OAuth organization provisioning for $providerName", e)
            throw e
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
            redirectToFrontendError(ERROR_FAILED)
        }
    }

    @POST
    @Path("/token-exchanges")
    fun exchangeTokenHandoff(payload: OAuthTokenExchangeRequest): Response
    {
        val code = payload.code?.trim()
        if (code.isNullOrBlank())
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("OAuth exchange code is required"))
                .build()
        }

        val handoff = oauthTokenHandoffService.consume(code)
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("OAuth exchange code is invalid or expired"))
                .build()

        return Response.ok(
            OAuthTokenExchangeResponse(
                accessToken = handoff.accessToken,
                idToken = handoff.idToken,
                isNewUser = handoff.isNewUser,
            )
        ).build()
    }

    @POST
    @Path("/link-confirm")
    fun linkConfirm(
        payload: OAuthLinkConfirmRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
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

            val claims = authenticationService.parseLinkTokenClaims(payload.linkToken!!)
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

            if (!appUser.isActive || appUser.deprovisionedAt != null || appUser.isTemporary)
            {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(ResponseError("Account is not eligible for sign-in"))
                    .build()
            }

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
            val tokenTriple = tokenIssuanceService.issueTokenTriple(appUser,
                userAgent = request.getHeader("User-Agent"),
                ipAddress = clientIpResolver.resolve(request),
            )
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

    /**
     * Sends the browser back to the sign-in page with a stable error code.
     *
     * Only codes from the companion object are ever emitted. Exception text is kept server side
     * because it can name the provider another account is linked to, which would turn a failed
     * sign-in into an account and provider oracle.
     */
    private fun redirectToFrontendError(errorCode: String): Response
    {
        val baseUrl = configurationService.baseUrl
        val encodedCode = URLEncoder.encode(errorCode, StandardCharsets.UTF_8)
        return Response.temporaryRedirect(
            URI.create("$baseUrl/sign-in?errorCode=$encodedCode")
        ).build()
    }

    private fun redirectToStepUpReturn(returnTo: String?): Response
    {
        val safePath = returnTo?.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.startsWith("/\\") } ?: "/exchanges"
        val separator = if (safePath.contains("?")) "&" else "?"
        val destination = "${configurationService.baseUrl.trimEnd('/')}$safePath${separator}stepUp=success"
        return Response.temporaryRedirect(URI.create(destination)).build()
    }
}
