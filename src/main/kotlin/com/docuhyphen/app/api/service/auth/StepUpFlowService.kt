package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.StepUpInitiateResponse
import com.docuhyphen.app.api.model.dto.StepUpResponse
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.StepUpCompleteRequest
import com.docuhyphen.app.api.resource.model.StepUpInitiateRequest
import com.docuhyphen.app.api.resource.model.StepUpOtpRegenerationRequest
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.identity.OrganizationIdpRuntimeCredentialService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

class StepUpRequestException(message: String) : RuntimeException(message)

@ApplicationScoped
class StepUpFlowService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val stepUpAuthService: StepUpAuthService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val stepUpMfaChallengeService: StepUpMfaChallengeService,
    private val oauthUserLinkingService: OAuthUserLinkingService,
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val oauthStateService: OAuthStateService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationIdpRuntimeCredentialService: OrganizationIdpRuntimeCredentialService,
    private val oauthRedirectUriResolver: OAuthRedirectUriResolver,
)
{
    companion object
    {
        private const val METHOD_INTERNAL_EMAIL_OTP = "INTERNAL_EMAIL_OTP"
        private const val METHOD_EXTERNAL_RELOGIN = "EXTERNAL_RELOGIN"
        private const val UNKNOWN_PEER_ADDRESS = "0.0.0.0"
        private const val NOT_AUTHENTICATED_MESSAGE = "Not authenticated."
        private const val TOO_MANY_REQUESTS_MESSAGE = "Too many requests."
    }

    fun initiate(payload: StepUpInitiateRequest, peerAddress: String?, requestId: String?): StepUpInitiateResponse
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException(NOT_AUTHENTICATED_MESSAGE)

        val ip = peerAddress ?: UNKNOWN_PEER_ADDRESS
        if (authRateLimitService.isLimited(
                key = "auth:step-up:initiate:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
            )
        )
        {
            throw AuthRequestRateLimitedException(TOO_MANY_REQUESTS_MESSAGE)
        }

        val links = oauthUserLinkingService.getLinksForUser(appUser.id)
        val hasInternalCredential = !appUser.password.isNullOrBlank()
        val internalLink = links.any { it.provider == IdentityProviderType.INTERNAL }

        if (hasInternalCredential || internalLink)
        {
            val actionDescription = payload.action?.trim()?.takeIf { it.isNotBlank() }
            val challenge = stepUpMfaChallengeService.initiate(appUser, ip, actionDescription)

            authAuditService.emit(
                action = "STEP_UP_INITIATE",
                outcome = "SUCCESS",
                actorId = appUser.id,
                requestId = requestId,
                reason = "Issued ${challenge.mfaType} challenge for step-up",
            )

            return StepUpInitiateResponse(
                method = METHOD_INTERNAL_EMAIL_OTP,
                message = challenge.message,
                mfaSessionId = challenge.sessionId,
                mfaType = challenge.mfaType,
                emailFallbackEnabled = challenge.emailFallbackEnabled,
            )
        }

        val externalLink = links.firstOrNull { it.provider != IdentityProviderType.INTERNAL }
            ?: throw StepUpRequestException("No available step-up method for this account.")

        val provider = identityProviderRegistry.getProvider(externalLink.provider)
        val token = authTokenContext.authToken.token
            ?: throw UnauthorizedException(NOT_AUTHENTICATED_MESSAGE)
        val claims = authenticationService.parseTokenClaims(token)
            ?: throw UnauthorizedException(NOT_AUTHENTICATED_MESSAGE)
        val sessionId = (claims["exchange_id"] as? String)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw UnauthorizedException("Session context missing for step-up.")

        val redirectUri = oauthRedirectUriResolver.resolve(externalLink.provider)

        val orgIdpConfigId = organizationIdentityPolicyService.findActiveProviderConfigIdForEmail(
            appUser.email,
            externalLink.provider,
        )
        val runtimeCredentials = organizationIdpRuntimeCredentialService.resolve(
            externalLink.provider,
            orgIdpConfigId,
        )
        val signedState = oauthStateService.createSignedState(
            flow = "stepup",
            provider = externalLink.provider,
            orgIdpConfigId = orgIdpConfigId,
            stepUpSessionId = sessionId,
            stepUpAppUserId = appUser.id,
            stepUpExpectedSubjectId = externalLink.externalSubjectId,
            stepUpReturnTo = normalizeReturnTo(payload.returnTo),
        )

        val authorizeUrl = provider.buildAuthorizationUrl(
            state = signedState.token,
            nonce = signedState.nonce,
            redirectUri = redirectUri,
            runtimeCredentials = runtimeCredentials,
            codeChallenge = signedState.codeChallenge,
            prompt = "login",
        )

        authAuditService.emit(
            action = "STEP_UP_INITIATE",
            outcome = "SUCCESS",
            actorId = appUser.id,
            requestId = requestId,
            reason = "Initiated external re-login for step-up",
        )

        return StepUpInitiateResponse(
            method = METHOD_EXTERNAL_RELOGIN,
            message = "Please re-authenticate with your identity provider.",
            provider = externalLink.provider.name,
            authorizeUrl = authorizeUrl,
        )
    }

    fun complete(payload: StepUpCompleteRequest, peerAddress: String?, requestId: String?): StepUpResponse
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException(NOT_AUTHENTICATED_MESSAGE)

        val ip = peerAddress ?: UNKNOWN_PEER_ADDRESS
        if (authRateLimitService.isLimited(
                key = "auth:step-up:complete:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInCompletionPerMinute(),
            )
        )
        {
            throw AuthRequestRateLimitedException(TOO_MANY_REQUESTS_MESSAGE)
        }

        val mfaSessionId = payload.mfaSessionId?.trim().orEmpty()
        val otp = payload.otp?.trim().orEmpty()
        if (mfaSessionId.isBlank() || otp.isBlank())
        {
            throw StepUpRequestException("Session and verification code are required.")
        }

        try
        {
            stepUpMfaChallengeService.complete(appUser, mfaSessionId, otp)
            stepUpAuthService.markFresh()
            authAuditService.emit(
                action = "STEP_UP_COMPLETE",
                outcome = "SUCCESS",
                actorId = appUser.id,
                requestId = requestId,
            )
            return StepUpResponse(fresh = true, message = "Authentication refreshed.")
        }
        catch (exception: Exception)
        {
            authAuditService.emit(
                action = "STEP_UP_COMPLETE",
                outcome = "DENY",
                actorId = appUser.id,
                reasonCode = RevocationReasonCode.STEP_UP_REQUIRED,
                requestId = requestId,
                reason = "Step-up OTP verification failed",
            )
            throw exception
        }
    }

    fun regenerateOtp(payload: StepUpOtpRegenerationRequest, peerAddress: String?, requestId: String?): StepUpResponse
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException(NOT_AUTHENTICATED_MESSAGE)

        val ip = peerAddress ?: UNKNOWN_PEER_ADDRESS
        if (authRateLimitService.isLimited(
                key = "auth:step-up:regenerate:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
            )
        )
        {
            throw AuthRequestRateLimitedException(TOO_MANY_REQUESTS_MESSAGE)
        }

        val mfaSessionId = payload.mfaSessionId?.trim().orEmpty()
        if (mfaSessionId.isBlank())
        {
            throw StepUpRequestException("Session is required.")
        }

        val challenge = stepUpMfaChallengeService.regenerateOrFallback(appUser, mfaSessionId, ip)
        authAuditService.emit(
            action = "STEP_UP_REGENERATE_OTP",
            outcome = "SUCCESS",
            actorId = appUser.id,
            requestId = requestId,
        )
        return StepUpResponse(
            fresh = false,
            message = challenge.message,
            mfaType = challenge.mfaType,
            emailFallbackEnabled = challenge.emailFallbackEnabled,
        )
    }

    private fun normalizeReturnTo(returnTo: String?): String
    {
        val trimmed = returnTo?.trim().orEmpty()
        if (trimmed.isBlank()) return "/exchanges"
        if (!trimmed.startsWith("/")) return "/exchanges"
        if (trimmed.startsWith("//")) return "/exchanges"
        return trimmed
    }
}
