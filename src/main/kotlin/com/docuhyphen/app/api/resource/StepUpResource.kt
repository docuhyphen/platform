package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.exception.InvalidOtpException
import com.docuhyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.docuhyphen.app.api.exception.OTPExpiredException
import com.docuhyphen.app.api.exception.TooManyRequestsException
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.auth.StepUpMfaChallengeService
import com.docuhyphen.app.api.service.auth.StepUpMfaRateLimitedException
import com.docuhyphen.app.api.repository.IdentityProviderLinkRepository
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.auth.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.Serializable

@Serializable
data class StepUpInitiateRequest(val returnTo: String? = null, val action: String? = null)

@Serializable
data class StepUpInitiateResponse(
    val method: String,
    val message: String,
    val mfaSessionId: String? = null,
    val provider: String? = null,
    val authorizeUrl: String? = null,
    val mfaType: String? = null,
    val emailFallbackEnabled: Boolean = false,
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
data class StepUpResponse(
    val fresh: Boolean,
    val message: String,
    val mfaType: String? = null,
    val emailFallbackEnabled: Boolean = false,
)

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
    private val stepUpMfaChallengeService: StepUpMfaChallengeService,
    private val identityProviderLinkRepository: IdentityProviderLinkRepository,
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val oauthStateService: OAuthStateService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val organizationIdpRuntimeCredentialService: OrganizationIdpRuntimeCredentialService,
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

        // Prefer the account's configured MFA method when an internal credential exists.
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

            return Response.ok(
                StepUpInitiateResponse(
                    method = METHOD_INTERNAL_EMAIL_OTP,
                    message = challenge.message,
                    mfaSessionId = challenge.sessionId,
                    mfaType = challenge.mfaType,
                    emailFallbackEnabled = challenge.emailFallbackEnabled,
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
        val sessionId = (claims["exchange_id"] as? String)
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

        return try
        {
            stepUpMfaChallengeService.complete(appUser, mfaSessionId, otp)
            stepUpAuthService.markFresh()
            authAuditService.emit(
                action = "STEP_UP_COMPLETE",
                outcome = "SUCCESS",
                actorId = appUser.id,
                requestId = requestId,
            )
            Response.ok(StepUpResponse(fresh = true, message = "Authentication refreshed.")).build()
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
            when (exception)
            {
                is InvalidOtpException,
                is MaxAttemptsOTPExceededException,
                is OTPExpiredException -> Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError(exception.message))
                    .build()
                is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError(exception.message))
                    .build()
                else -> throw exception
            }
        }
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

        return try
        {
            val challenge = stepUpMfaChallengeService.regenerateOrFallback(appUser, mfaSessionId, ip)
            authAuditService.emit(
                action = "STEP_UP_REGENERATE_OTP",
                outcome = "SUCCESS",
                actorId = appUser.id,
                requestId = requestId,
            )
            Response.ok(
                StepUpResponse(
                    fresh = false,
                    message = challenge.message,
                    mfaType = challenge.mfaType,
                    emailFallbackEnabled = challenge.emailFallbackEnabled,
                )
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is StepUpMfaRateLimitedException -> Response.status(429)
                    .entity(ResponseError(
                        errorMessage = exception.message,
                        reasonCode = "OTP_RATE_LIMITED",
                        retryAfterSeconds = exception.retryAfterSeconds,
                    ))
                    .build()
                is TooManyRequestsException -> Response.status(429)
                    .entity(ResponseError(exception.message))
                    .build()
                is InvalidOtpException,
                is MaxAttemptsOTPExceededException -> Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError(exception.message))
                    .build()
                is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError(exception.message))
                    .build()
                else -> throw exception
            }
        }
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
