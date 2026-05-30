package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationStatus
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.EMAIL
import com.docuhyphen.app.api.repository.IdentityProviderLinkRepository
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.communication.MfaService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant

@Serializable
data class StepUpInitiateRequest(val returnTo: String? = null)

@Serializable
data class StepUpInitiateResponse(
    val method: String,
    val message: String,
    val mfaSessionId: String? = null,
    val provider: String? = null,
    val authorizeUrl: String? = null,
)

@Serializable
data class StepUpCompleteRequest(
    val mfaSessionId: String? = null,
    val otp: String? = null,
)

@Serializable
data class StepUpOtpRegenerationRequest(
    val mfaSessionId: String? = null,
)

@Serializable
data class StepUpResponse(val fresh: Boolean, val message: String)

@Path("/auth/step-up")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class StepUpResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val stepUpAuthService: StepUpAuthService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val mfaService: MfaService,
    private val otpService: OtpService,
    private val identityProviderLinkRepository: IdentityProviderLinkRepository,
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val oauthStateService: OAuthStateService,
)
{
    companion object
    {
        private const val METHOD_INTERNAL_EMAIL_OTP = "INTERNAL_EMAIL_OTP"
        private const val METHOD_EXTERNAL_RELOGIN = "EXTERNAL_RELOGIN"
    }

    @POST
    @Path("/initiate")
    fun initiate(
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
        payload: StepUpInitiateRequest,
    ): Response
    {
        val appUser = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Not authenticated."))
                .build()

        val ip = request.remoteAddress()?.host() ?: "0.0.0.0"
        if (authRateLimitService.isLimited(
                key = "auth:step-up:initiate:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
            ))
        {
            return Response.status(429).entity(ResponseError("Too many requests.")).build()
        }

        val links = identityProviderLinkRepository.findAllByAppUserId(appUser.id)
        val hasInternalCredential = !appUser.password.isNullOrBlank()
        val internalLink = links.any { it.provider == IdentityProviderType.INTERNAL }

        // Prefer internal step-up (email OTP) when an internal credential exists.
        if (hasInternalCredential || internalLink)
        {
            val mfaSession = mfaService.createMfaSession(
                user = appUser,
                mfaType = EMAIL,
                ipAddress = ip,
            )
            mfaService.doEmailMFA(appUser, mfaSession.mfaToken!!)

            authAuditService.emit(
                action = "STEP_UP_INITIATE",
                outcome = "SUCCESS",
                actorId = appUser.id,
                requestId = requestId,
                reason = "Issued email OTP for step-up",
            )

            return Response.ok(
                StepUpInitiateResponse(
                    method = METHOD_INTERNAL_EMAIL_OTP,
                    message = "A verification code was sent to your email.",
                    mfaSessionId = mfaSession.id?.toString(),
                )
            ).build()
        }

        val externalLink = links.firstOrNull { it.provider != IdentityProviderType.INTERNAL }
            ?: return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("No available step-up method for this account."))
                .build()

        val provider = identityProviderRegistry.getProvider(externalLink.provider)
        val token = authTokenContext.authToken.token
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Not authenticated."))
                .build()
        val claims = authenticationService.parseTokenClaims(token)
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Not authenticated."))
                .build()
        val sessionId = (claims["session_id"] as? String)
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Session context missing for step-up."))
                .build()

        val redirectUri = when (externalLink.provider)
        {
            IdentityProviderType.MICROSOFT -> configurationService.microsoftOAuthRedirectUri
            IdentityProviderType.GOOGLE -> configurationService.googleOAuthRedirectUri
            else -> throw IllegalArgumentException("Unsupported external provider for step-up")
        }

        val signedState = oauthStateService.createSignedState(
            flow = "stepup",
            provider = externalLink.provider,
            stepUpSessionId = sessionId,
            stepUpAppUserId = appUser.id,
            stepUpExpectedSubjectId = externalLink.externalSubjectId,
            stepUpReturnTo = normalizeReturnTo(payload.returnTo),
        )

        val authorizeUrl = provider.buildAuthorizationUrl(
            state = signedState.token,
            nonce = signedState.nonce,
            redirectUri = redirectUri,
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

        return Response.ok(
            StepUpInitiateResponse(
                method = METHOD_EXTERNAL_RELOGIN,
                message = "Please re-authenticate with your identity provider.",
                provider = externalLink.provider.name,
                authorizeUrl = authorizeUrl,
            )
        ).build()
    }

    @POST
    @Path("/complete")
    fun complete(
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
        payload: StepUpCompleteRequest,
    ): Response
    {
        val appUser = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Not authenticated."))
                .build()

        val ip = request.remoteAddress()?.host() ?: "0.0.0.0"
        if (authRateLimitService.isLimited(
                key = "auth:step-up:complete:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInCompletionPerMinute(),
            ))
        {
            return Response.status(429).entity(ResponseError("Too many requests.")).build()
        }

        val mfaSessionId = payload.mfaSessionId?.trim().orEmpty()
        val otp = payload.otp?.trim().orEmpty()
        if (mfaSessionId.isBlank() || otp.isBlank())
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Session and verification code are required."))
                .build()
        }

        val mfaRecord = mfaService.getMfaRecordByEmailAndSessionId(appUser.email, mfaSessionId)
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Invalid verification code."))
                .build()

        if (mfaRecord.mfaType != EMAIL)
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Unsupported step-up verification method."))
                .build()
        }

        if (mfaRecord.status == MultifactorAuthenticationStatus.COMPLETED)
        {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Verification code already used."))
                .build()
        }

        if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
        {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Too many invalid attempts."))
                .build()
        }

        if (mfaRecord.expiryDateTime?.before(Timestamp.from(Instant.now())) == true)
        {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Verification code expired."))
                .build()
        }

        mfaRecord.attemptCount++
        if (mfaRecord.attemptCount > configurationService.getMaxSignInAttempts())
        {
            mfaRecord.status = MultifactorAuthenticationStatus.LOCKED
            mfaService.updateRecord(mfaRecord)
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Too many invalid attempts."))
                .build()
        }

        if (!otpService.verifyEmailOtp(otp, mfaRecord.mfaToken!!))
        {
            mfaService.updateRecord(mfaRecord)
            authAuditService.emit(
                action = "STEP_UP_COMPLETE",
                outcome = "DENY",
                actorId = appUser.id,
                reasonCode = RevocationReasonCode.STEP_UP_REQUIRED,
                requestId = requestId,
                reason = "Step-up OTP verification failed",
            )
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Invalid verification code."))
                .build()
        }

        mfaRecord.status = MultifactorAuthenticationStatus.COMPLETED
        mfaService.updateRecord(mfaRecord)
        mfaService.removeMfaRecord(mfaRecord)

        stepUpAuthService.markFresh()
        authAuditService.emit(
            action = "STEP_UP_COMPLETE",
            outcome = "SUCCESS",
            actorId = appUser.id,
            requestId = requestId,
        )
        return Response.ok(StepUpResponse(fresh = true, message = "Authentication refreshed.")).build()
    }

    @POST
    @Path("/otp-regeneration")
    fun regenerateOtp(
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
        payload: StepUpOtpRegenerationRequest,
    ): Response
    {
        val appUser = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Not authenticated."))
                .build()

        val ip = request.remoteAddress()?.host() ?: "0.0.0.0"
        if (authRateLimitService.isLimited(
                key = "auth:step-up:regenerate:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
            ))
        {
            return Response.status(429).entity(ResponseError("Too many requests.")).build()
        }

        val mfaSessionId = payload.mfaSessionId?.trim().orEmpty()
        if (mfaSessionId.isBlank())
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Session is required."))
                .build()
        }

        val mfaRecord = mfaService.getMfaRecordByEmailAndSessionId(appUser.email, mfaSessionId)
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Invalid step-up session."))
                .build()

        if (mfaRecord.status == MultifactorAuthenticationStatus.COMPLETED)
        {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Step-up already completed."))
                .build()
        }

        if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
        {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Too many invalid attempts."))
                .build()
        }

        val resendCooldownSeconds = configurationService.getSignInResendCooldownSeconds()
        val cooldownUntil = mfaRecord.createdDate.toInstant().plusSeconds(resendCooldownSeconds)
        if (Instant.now().isBefore(cooldownUntil))
        {
            return Response.status(429)
                .entity(ResponseError("Please wait before requesting another verification code."))
                .build()
        }

        mfaService.enforceRateLimits(appUser.email, mfaRecord.ipAddress ?: ip)
        val newOtp = mfaService.regenerateOtp(mfaRecord)
        mfaService.doEmailMFA(appUser, newOtp)

        authAuditService.emit(
            action = "STEP_UP_REGENERATE_OTP",
            outcome = "SUCCESS",
            actorId = appUser.id,
            requestId = requestId,
        )

        return Response.ok(StepUpResponse(fresh = false, message = "A new verification code has been sent.")).build()
    }

    private fun normalizeReturnTo(returnTo: String?): String
    {
        val trimmed = returnTo?.trim().orEmpty()
        if (trimmed.isBlank()) return "/sharing-sessions"
        if (!trimmed.startsWith("/")) return "/sharing-sessions"
        if (trimmed.startsWith("//")) return "/sharing-sessions"
        return trimmed
    }
}
