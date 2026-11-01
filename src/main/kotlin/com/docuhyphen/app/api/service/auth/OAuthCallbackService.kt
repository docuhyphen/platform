package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.OAuthRedirect
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.identity.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class OAuthCallbackService @Inject constructor(
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val oauthUserLinkingService: OAuthUserLinkingService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
    private val oauthStateService: OAuthStateService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationIdpRuntimeCredentialService: OrganizationIdpRuntimeCredentialService,
    private val stepUpAuthService: StepUpAuthService,
    private val oauthTokenHandoffService: OAuthTokenHandoffService,
    private val oauthRedirectUriResolver: OAuthRedirectUriResolver,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OAuthCallbackService::class.java)

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

    fun handleCallback(
        providerName: String,
        code: String?,
        state: String?,
        requestId: String?,
        clientIp: String,
        userAgent: String?,
    ): OAuthRedirect
    {
        return try
        {
            completeCallback(providerName, code, state, requestId, clientIp, userAgent)
        }
        catch (exception: ExternalProviderAlreadyLinkedException)
        {
            logger.warn("External provider conflict during OAuth callback for $providerName: ${exception.message}")
            authAuditService.emit(
                action = "OAUTH_CALLBACK",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            frontendErrorRedirect(ERROR_PROVIDER_CONFLICT)
        }
        catch (exception: UnverifiedExternalEmailException)
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
            frontendErrorRedirect(ERROR_EMAIL_NOT_VERIFIED)
        }
    }

    fun callbackFailed(requestId: String?): OAuthRedirect
    {
        authAuditService.emit(
            action = "OAUTH_CALLBACK",
            outcome = "DENY",
            reasonCode = RevocationReasonCode.OIDC_VALIDATION_FAILED,
            requestId = requestId,
        )
        return frontendErrorRedirect(ERROR_FAILED)
    }

    private fun completeCallback(
        providerName: String,
        code: String?,
        state: String?,
        requestId: String?,
        clientIp: String,
        userAgent: String?,
    ): OAuthRedirect
    {
        if (authRateLimitService.isLimited(
                key = "auth:oauth:callback:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitCallbackPerMinute(),
            )
        )
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
            return frontendErrorRedirect(ERROR_RATE_LIMITED)
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
            return frontendErrorRedirect(ERROR_INVALID_REQUEST)
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
            return frontendErrorRedirect(ERROR_INVALID_STATE)
        }

        val providerType = IdentityProviderType.valueOf(providerName.uppercase())
        val provider = identityProviderRegistry.getProvider(providerType)
        val redirectUri = oauthRedirectUriResolver.resolve(providerType)

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
                return frontendErrorRedirect(ERROR_INVALID_STATE)
            }

        val runtimeCredentials =
            organizationIdpRuntimeCredentialService.resolve(providerType, verifiedState.orgIdpConfigId)

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
                ?: return frontendErrorRedirect(ERROR_LINK_CONTEXT_MISSING)
            val linkingUser = appUserService.getById(linkingUserId)
                ?: return frontendErrorRedirect(ERROR_LINK_CONTEXT_MISSING)
            if (!linkingUser.isActive || linkingUser.deprovisionedAt != null || linkingUser.isTemporary)
            {
                return frontendErrorRedirect(ERROR_ACCOUNT_NOT_ELIGIBLE)
            }

            oauthUserLinkingService.createLink(
                appUser = linkingUser,
                provider = providerType,
                externalSubjectId = userInfo.subjectId,
                externalEmail = userInfo.email,
            )
            val tokenTriple = tokenIssuanceService.issueTokenTriple(
                linkingUser,
                userAgent = userAgent,
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
            return OAuthRedirect(URI.create(callbackUrl), listOf(refreshCookie, csrfCookie))
        }

        if (verifiedState.flow.equals("stepup", ignoreCase = true))
        {
            val sessionId = verifiedState.stepUpSessionId
                ?: return frontendErrorRedirect(ERROR_STEP_UP_MISMATCH)
            val actorId = verifiedState.stepUpAppUserId
                ?: return frontendErrorRedirect(ERROR_STEP_UP_MISMATCH)
            val expectedSubject = verifiedState.stepUpExpectedSubjectId
                ?: return frontendErrorRedirect(ERROR_STEP_UP_MISMATCH)

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
                return frontendErrorRedirect(ERROR_STEP_UP_MISMATCH)
            }

            stepUpAuthService.markFresh(sessionId)
            authAuditService.emit(
                action = "STEP_UP_OAUTH_CALLBACK",
                outcome = "SUCCESS",
                actorId = actorId,
                requestId = requestId,
            )
            return stepUpReturnRedirect(verifiedState.stepUpReturnTo)
        }

        val trustedOrganization = organizationIdentityPolicyService.resolveTrustedOrganizationForOAuth(
            email = userInfo.email,
            provider = providerType,
            orgIdpConfigId = verifiedState.orgIdpConfigId,
        )

        val result = oauthUserLinkingService.linkOrCreateUser(providerType, userInfo, trustedOrganization)

        if (result.requiresLinkConfirmation)
        {
            val baseUrl = configurationService.baseUrl
            val params = "provider=${providerType.name}" +
                    "&email=${URLEncoder.encode(userInfo.email, StandardCharsets.UTF_8)}" +
                    "&linkToken=${URLEncoder.encode(result.linkToken!!, StandardCharsets.UTF_8)}"
            val linkConfirmRedirect = OAuthRedirect(URI.create("$baseUrl/oauth/link-confirm?$params"))
            authAuditService.emit(
                action = "OAUTH_CALLBACK",
                outcome = "SUCCESS",
                actorId = result.appUser.id,
                requestId = requestId,
            )
            return linkConfirmRedirect
        }

        val tokenTriple = tokenIssuanceService.issueTokenTriple(
            result.appUser,
            userAgent = userAgent,
            ipAddress = clientIp,
        )
        val refreshCookie =
            tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, result.appUser)
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

        val signedInRedirect = OAuthRedirect(URI.create(callbackUrl), listOf(refreshCookie, csrfCookie))
        authAuditService.emit(
            action = "OAUTH_CALLBACK",
            outcome = "SUCCESS",
            actorId = result.appUser.id,
            requestId = requestId,
        )
        return signedInRedirect
    }

    private fun frontendErrorRedirect(errorCode: String): OAuthRedirect
    {
        val baseUrl = configurationService.baseUrl
        val encodedCode = URLEncoder.encode(errorCode, StandardCharsets.UTF_8)
        return OAuthRedirect(URI.create("$baseUrl/sign-in?errorCode=$encodedCode"))
    }

    private fun stepUpReturnRedirect(returnTo: String?): OAuthRedirect
    {
        val safePath =
            returnTo?.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.startsWith("/\\") } ?: "/exchanges"
        val separator = if (safePath.contains("?")) "&" else "?"
        val destination = "${configurationService.baseUrl.trimEnd('/')}$safePath${separator}stepUp=success"
        return OAuthRedirect(URI.create(destination))
    }
}
