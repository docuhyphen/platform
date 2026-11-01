package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InactiveAccountException
import com.docuhyphen.app.api.exception.PasswordChangeRequiredException
import com.docuhyphen.app.api.exception.SignUpRequiredException
import com.docuhyphen.app.api.exception.TemporaryPasswordExpiredException
import com.docuhyphen.app.api.model.SignInResponseMapper
import com.docuhyphen.app.api.model.auth.IssuedSessionTokens
import com.docuhyphen.app.api.model.auth.SessionCookies
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SignInFlowService @Inject constructor(
    private val signInService: SignInService,
    private val signInLookupService: SignInLookupService,
    private val appUserService: AppUserService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
)
{
    fun lookup(payload: SignInLookupRequest, clientIp: String, requestId: String?): SignInLookupResponse
    {
        return try
        {
            signInLookupService.lookup(
                payload = payload,
                clientIp = clientIp,
                requestId = requestId,
            )
        }
        catch (exception: InvalidSignInLookupException)
        {
            throw exception
        }
        catch (exception: SignInLookupRateLimitedException)
        {
            throw exception
        }
        catch (exception: Exception)
        {
            authAuditService.emit(
                action = "SIGN_IN_LOOKUP",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            throw exception
        }
    }

    fun initiate(payload: SignInRequest, clientIp: String, requestId: String?): SignInResponse
    {
        if (authRateLimitService.isLimited(
                key = "auth:sign-in:initiate:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
            )
        )
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_INITIATE,
                severity = SecurityIncidentSeverity.HIGH,
                requestId = requestId,
                details = "ip=$clientIp",
            )
            authAuditService.emit(
                action = "SIGN_IN_INITIATE",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            throw AuthRequestRateLimitedException()
        }

        return try
        {
            val mfaSession = signInService.initiateSignIn(payload.email, payload.password, clientIp)
            val signInResponse = SignInResponseMapper.toResponse(mfaSession)
            authAuditService.emit(
                action = "SIGN_IN_INITIATE",
                outcome = "SUCCESS",
                requestId = requestId,
            )
            signInResponse
        }
        catch (exception: Exception)
        {
            authAuditService.emit(
                action = "SIGN_IN_INITIATE",
                outcome = "DENY",
                reasonCode = when (exception)
                {
                    is InactiveAccountException -> RevocationReasonCode.DEPROVISIONED
                    else -> RevocationReasonCode.SECURITY_POLICY
                },
                requestId = requestId,
                reason = when (exception)
                {
                    is SignUpRequiredException -> "Recipient placeholder account requires sign-up completion"

                    is PasswordChangeRequiredException,
                    is TemporaryPasswordExpiredException -> "Temporary password requires reset"

                    else -> null
                },
            )
            throw exception
        }
    }

    fun complete(
        payload: SignInCompletionRequest,
        clientIp: String,
        userAgent: String?,
        requestId: String?,
    ): IssuedSessionTokens
    {
        if (authRateLimitService.isLimited(
                key = "auth:sign-in:completion:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitSignInCompletionPerMinute(),
            )
        )
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_COMPLETION,
                severity = SecurityIncidentSeverity.HIGH,
                requestId = requestId,
                details = "ip=$clientIp",
            )
            authAuditService.emit(
                action = "SIGN_IN_COMPLETION",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            throw AuthRequestRateLimitedException()
        }

        return try
        {
            val tokenTriple = signInService.completeSignIn(
                payload.email,
                payload.otp,
                payload.mfaSessionId,
                userAgent = userAgent,
                ipAddress = clientIp,
            )
            val policyUser = payload.email
                ?.trim()
                ?.lowercase()
                ?.let { appUserService.findByEmail(it) }
            val refreshCookie = if (policyUser != null)
            {
                tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, policyUser)
            }
            else
            {
                tokenIssuanceService.buildRefreshTokenCookie(tokenTriple.refreshToken)
            }
            val csrfToken = tokenIssuanceService.generateCsrfToken()
            val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)
            authAuditService.emit(
                action = "SIGN_IN_COMPLETION",
                outcome = "SUCCESS",
                requestId = requestId,
            )
            IssuedSessionTokens(
                accessToken = tokenTriple.accessToken,
                idToken = tokenTriple.idToken,
                cookies = SessionCookies(refreshCookie, csrfCookie),
            )
        }
        catch (exception: Exception)
        {
            authAuditService.emit(
                action = "SIGN_IN_COMPLETION",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            throw exception
        }
    }

    fun regenerateOtp(payload: ResendOtpRequest, clientIp: String, requestId: String?): SignInResponse
    {
        if (authRateLimitService.isLimited(
                key = "auth:sign-in:otp-regeneration:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitOtpRegenerationPerMinute(),
            )
        )
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_COMPLETION,
                severity = SecurityIncidentSeverity.MEDIUM,
                requestId = requestId,
                details = "endpoint=otp-regeneration;ip=$clientIp",
            )
            throw AuthRequestRateLimitedException()
        }

        val mfaSession = signInService.redoMfa(payload.email, payload.mfaSessionId)
        return SignInResponse("A new verification has been sent to your email", mfaSession.id.toString())
    }

    fun createEmailFallbackChallenge(
        payload: EmailFallbackChallengeRequest,
        sessionId: String,
        clientIp: String,
        requestId: String?,
    ): SignInResponse
    {
        if (authRateLimitService.isLimited(
                key = "auth:sign-in:email-challenge:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitOtpRegenerationPerMinute(),
            )
        )
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_COMPLETION,
                severity = SecurityIncidentSeverity.MEDIUM,
                requestId = requestId,
                details = "endpoint=email-challenge;ip=$clientIp",
            )
            throw AuthRequestRateLimitedException()
        }

        val mfaSession = signInService.createEmailFallbackChallenge(payload.email, sessionId)
        return SignInResponseMapper.toResponse(mfaSession)
    }
}
