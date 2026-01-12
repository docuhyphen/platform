package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.SignInService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
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
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignInResource::class.java)
    }

    @POST
    @Path("/initiate")
    fun signIn(
        @Context request: io.vertx.core.http.HttpServerRequest,
        payload: SignInRequest
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.delayEndpoint(4000, 6000)

            val mfaSession = with(payload) {

                signInService.initiateSignIn(email, password, getClientIpAddress(request))
            }

            val signInResponse = SignInResponse("", mfaSession.id.toString())
            Response.ok(signInResponse).build()
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

                is InvalidSignInCredentialsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error initiating sign in", exception)
                    val responseError = ResponseError("A server error occurred while signing in.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/completion")
    fun completeSignIn(
        payload: SignInCompletionRequest
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.delayEndpoint(1000, 3000)

            val signInToken = with(payload) {
                signInService.completeSignIn(email, otp, mfaSessionId)
            }
            val signInCompletionResponse = SignInCompletionResponse(signInToken)
            Response.ok(signInCompletionResponse).build()
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
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("Something went wrong while trying to complete sign-in.")
                    logger.error("Error completing sign-in", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/otp-regeneration")
    fun resendOtp(
        payload: ResendOtpRequest
    ): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.delayEndpoint(3000, 6000)

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
                    val responseError = ResponseError("Something went wrong while trying to resend OTP.")
                    logger.error("Error resending OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
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

        // If we got a comma-separated list (from X-Forwarded-For), take the first one
        if (!ipAddress.isNullOrBlank() && ipAddress.contains(","))
        {
            ipAddress = ipAddress.split(",")[0].trim()
        }

        return ipAddress
    }
}
