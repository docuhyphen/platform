package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.auth.operations.SignInResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.*
import com.docuhyphen.app.api.service.identity.IdentityProviderNotAllowedException
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class SignInResource @Inject constructor(
    private val signInFlowService: SignInFlowService,
    private val clientIpResolver: ClientIpResolver,
) : SignInResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignInResource::class.java)
        private const val TOO_MANY_REQUESTS = 429
    }

    override fun lookupSignInMethod(
        request: HttpServerRequest,
        requestId: String?,
        payload: SignInLookupRequest,
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(1500) {
                Response.ok(
                    signInFlowService.lookup(
                        payload = payload,
                        clientIp = clientIpResolver.resolve(request),
                        requestId = requestId,
                    )
                ).build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is InvalidSignInLookupException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is SignInLookupRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error during sign-in lookup", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred during sign-in lookup."))
                        .build()
                }
            }
        }
    }

    override fun signIn(
        request: HttpServerRequest,
        requestId: String?,
        payload: SignInRequest,
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(2000) {
                Response.ok(
                    signInFlowService.initiate(
                        payload = payload,
                        clientIp = clientIpResolver.resolve(request),
                        requestId = requestId,
                    )
                ).build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException,
                is TooManyRequestsException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is InvalidSignInCredentialsException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is InactiveAccountException,
                is IdentityProviderNotAllowedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SignUpRequiredException ->
                    Response.status(FORBIDDEN)
                        .entity(ResponseError(errorMessage = exception.message, reasonCode = "SIGN_UP_REQUIRED"))
                        .build()

                is PasswordChangeRequiredException ->
                    Response.status(FORBIDDEN)
                        .entity(ResponseError(errorMessage = exception.message, reasonCode = "PASSWORD_CHANGE_REQUIRED"))
                        .build()

                is TemporaryPasswordExpiredException ->
                    Response.status(FORBIDDEN)
                        .entity(ResponseError(errorMessage = exception.message, reasonCode = "TEMP_PASSWORD_EXPIRED"))
                        .build()

                else ->
                {
                    logger.error("Error initiating sign in", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while signing in."))
                        .build()
                }
            }
        }
    }

    override fun completeSignIn(
        request: HttpServerRequest,
        requestId: String?,
        payload: SignInCompletionRequest,
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(1200) {
                val issued = signInFlowService.complete(
                    payload = payload,
                    clientIp = clientIpResolver.resolve(request),
                    userAgent = request.getHeader(AuthHttp.USER_AGENT_HEADER),
                    requestId = requestId,
                )
                Response.ok(SignInCompletionResponse(accessToken = issued.accessToken, idToken = issued.idToken))
                    .cookie(issued.cookies.refreshCookie, issued.cookies.csrfCookie)
                    .build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is OTPExpiredException,
                is EmailRequiredException,
                is MaxAttemptsOTPExceededException,
                is InvalidOtpException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error completing sign-in", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Something went wrong while trying to complete sign-in."))
                        .build()
                }
            }
        }
    }

    override fun resendOtp(
        request: HttpServerRequest,
        requestId: String?,
        payload: ResendOtpRequest,
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(1200) {
                Response.ok(
                    signInFlowService.regenerateOtp(
                        payload = payload,
                        clientIp = clientIpResolver.resolve(request),
                        requestId = requestId,
                    )
                ).build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException,
                is TooManyRequestsException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is InvalidSignInCredentialsException,
                is MaxAttemptsOTPExceededException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error resending OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Something went wrong while trying to resend verification code."))
                        .build()
                }
            }
        }
    }

    override fun createEmailFallbackChallenge(
        request: HttpServerRequest,
        requestId: String?,
        sessionId: String,
        payload: EmailFallbackChallengeRequest,
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(1200) {
                Response.status(CREATED)
                    .entity(
                        signInFlowService.createEmailFallbackChallenge(
                            payload = payload,
                            sessionId = sessionId,
                            clientIp = clientIpResolver.resolve(request),
                            requestId = requestId,
                        )
                    )
                    .build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is AuthRequestRateLimitedException,
                is TooManyRequestsException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

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
        }
    }
}
