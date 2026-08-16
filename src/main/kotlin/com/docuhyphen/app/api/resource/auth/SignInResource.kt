package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.model.SignInResponseMapper
import com.docuhyphen.app.api.repository.identity.IdentityProviderLinkRepository
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.auth.InvalidSignInLookupException
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.SignInLookupRateLimitedException
import com.docuhyphen.app.api.service.auth.SignInLookupService
import com.docuhyphen.app.api.service.auth.SignInService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.identity.IdentityProviderNotAllowedException
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory

@Path("/auth/sign-in")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignInResource @Inject constructor(
    private val signInService: SignInService,
    private val signInLookupService: SignInLookupService,
    private val appUserService: AppUserService,
    private val identityProviderLinkRepository: IdentityProviderLinkRepository,
    private val tokenIssuanceService: TokenIssuanceService,
    private val configurationService: ConfigurationService,
    private val oauthStateService: OAuthStateService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
    private val clientIpResolver: ClientIpResolver,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignInResource::class.java)
    }

    @POST
    @Path("/lookup")
    fun lookupSignInMethod(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: SignInLookupRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1500) {
            try
            {
                Response.ok(
                    signInLookupService.lookup(
                        payload = payload,
                        clientIp = clientIpResolver.resolve(request),
                        requestId = requestId,
                    )
                ).build()
            }
            catch (exception: InvalidSignInLookupException)
            {
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError(exception.message))
                    .build()
            }
            catch (exception: SignInLookupRateLimitedException)
            {
                Response.status(429)
                    .entity(ResponseError(exception.message))
                    .build()
            }
            catch (exception: Exception)
            {
                logger.error("Error during sign-in lookup", exception)
                authAuditService.emit(
                    action = "SIGN_IN_LOOKUP",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                Response.status(INTERNAL_SERVER_ERROR)
                    .entity(ResponseError("An error occurred during sign-in lookup."))
                    .build()
            }
        }
    }

    @POST
    @Path("/initiate")
    fun signIn(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: SignInRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(2000) { try
        {
            val clientIp = clientIpResolver.resolve(request)
            if (authRateLimitService.isLimited(
                    key = "auth:sign-in:initiate:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
                ))
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
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val mfaSession = with(payload) {

                signInService.initiateSignIn(email, password, clientIp)
            }

            val signInResponse = SignInResponseMapper.toResponse(mfaSession)
            Response.ok(signInResponse).build()
                .also {
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "SUCCESS",
                        requestId = requestId,
                    )
                }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is TooManyRequestsException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(429).entity(responseError).build()
                }

                is InvalidSignInCredentialsException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                is InactiveAccountException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.DEPROVISIONED,
                        requestId = requestId,
                    )
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is IdentityProviderNotAllowedException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is SignUpRequiredException ->
                {
                    val responseError = ResponseError(
                        errorMessage = exception.message,
                        reasonCode = "SIGN_UP_REQUIRED",
                    )
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                        reason = "Recipient placeholder account requires sign-up completion",
                    )
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is PasswordChangeRequiredException,
                is TemporaryPasswordExpiredException ->
                {
                    val reasonCode = when (exception)
                    {
                        is PasswordChangeRequiredException -> "PASSWORD_CHANGE_REQUIRED"
                        else -> "TEMP_PASSWORD_EXPIRED"
                    }
                    val responseError = ResponseError(
                        errorMessage = exception.message,
                        reasonCode = reasonCode,
                    )
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                        reason = "Temporary password requires reset",
                    )
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error initiating sign in", exception)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    val responseError = ResponseError("A server error occurred while signing in.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
        }
    }

    @POST
    @Path("/completion")
    fun completeSignIn(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: SignInCompletionRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) { try
        {
            val clientIp = clientIpResolver.resolve(request)
            if (authRateLimitService.isLimited(
                    key = "auth:sign-in:completion:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitSignInCompletionPerMinute(),
                ))
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
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val tokenTriple = with(payload) {
                signInService.completeSignIn(email, otp, mfaSessionId,
                    userAgent = request.getHeader("User-Agent"),
                    ipAddress = clientIp,
                )
            }

            val signInCompletionResponse = SignInCompletionResponse(
                accessToken = tokenTriple.accessToken,
                idToken = tokenTriple.idToken,
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
            Response.ok(signInCompletionResponse).cookie(refreshCookie, csrfCookie).build()
                .also {
                    authAuditService.emit(
                        action = "SIGN_IN_COMPLETION",
                        outcome = "SUCCESS",
                        requestId = requestId,
                    )
                }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OTPExpiredException,
                is EmailRequiredException,
                is MaxAttemptsOTPExceededException,
                is InvalidOtpException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_COMPLETION",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("Something went wrong while trying to complete sign-in.")
                    logger.error("Error completing sign-in", exception)
                    authAuditService.emit(
                        action = "SIGN_IN_COMPLETION",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
        }
    }

    @POST
    @Path("/otp-regeneration")
    fun resendOtp(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: ResendOtpRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) { try
        {
            val clientIp = clientIpResolver.resolve(request)
            if (authRateLimitService.isLimited(
                    key = "auth:sign-in:otp-regeneration:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitOtpRegenerationPerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_COMPLETION,
                    severity = SecurityIncidentSeverity.MEDIUM,
                    requestId = requestId,
                    details = "endpoint=otp-regeneration;ip=$clientIp",
                )
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val mfaSession = with(payload) {
                signInService.redoMfa(email, mfaSessionId)
            }

            val resendOtpResponse = SignInResponse("A new verification has been sent to your email", mfaSession.id.toString())
            Response.ok(resendOtpResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is TooManyRequestsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(429).entity(responseError).build()
                }

                is InvalidSignInCredentialsException,
                is MaxAttemptsOTPExceededException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("Something went wrong while trying to resend verification code.")
                    logger.error("Error resending OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
        }
    }

    @POST
    @Path("/mfa-sessions/{sessionId}/email-challenges")
    fun createEmailFallbackChallenge(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        @PathParam("sessionId") sessionId: String,
        payload: EmailFallbackChallengeRequest,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) { try
        {
            val clientIp = clientIpResolver.resolve(request)
            if (authRateLimitService.isLimited(
                    key = "auth:sign-in:email-challenge:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitOtpRegenerationPerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_COMPLETION,
                    severity = SecurityIncidentSeverity.MEDIUM,
                    requestId = requestId,
                    details = "endpoint=email-challenge;ip=$clientIp",
                )
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val mfaSession = signInService.createEmailFallbackChallenge(payload.email, sessionId)
            Response.status(Response.Status.CREATED)
                .entity(SignInResponseMapper.toResponse(mfaSession))
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is TooManyRequestsException -> Response.status(429).entity(ResponseError(exception.message)).build()
                is InvalidSignInCredentialsException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()
                else ->
                {
                    logger.error("Error creating email fallback challenge", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Could not create an email fallback challenge."))
                        .build()
                }
            }
        } }
    }
}
