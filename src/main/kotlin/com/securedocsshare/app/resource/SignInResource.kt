package com.securedocsshare.app.resource

import com.securedocsshare.app.api.model.EmailRequiredException
import com.securedocsshare.app.api.model.InvalidOtpException
import com.securedocsshare.app.api.model.InvalidSignInCredentialsException
import com.securedocsshare.app.api.model.OTPExpiredException
import com.securedocsshare.app.api.model.SignInCompletionRequest
import com.securedocsshare.app.api.model.ResponseError
import com.securedocsshare.app.api.model.SignInCompletionResponse
import com.securedocsshare.app.api.model.SignInRequest
import com.securedocsshare.app.api.model.SignInResponse
import com.securedocsshare.app.service.SignInService
import jakarta.inject.Inject
import jakarta.ws.rs.*
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
    fun signIn(payload: SignInRequest): Response
    {
        return try
        {
            with(payload) {
                signInService.initiateSignIn(payload.email, payload.password)
                val signInResponse = SignInResponse("")
                Response.ok(signInResponse).build()
            }
        }
        catch (exception: InvalidSignInCredentialsException)
        {
            val responseError = ResponseError(exception.message)
            Response.status(UNAUTHORIZED).entity(responseError).build()
        }
        catch (exception: Exception)
        {
            Response.status(INTERNAL_SERVER_ERROR).entity(mapOf("error" to exception.message)).build()
        }
    }

    @POST
    @Path("/completion")
    fun completeSignIn(payload: SignInCompletionRequest): Response
    {
        return try
        {
            val signInToken = with(payload) {
                signInService.completeSignIn(email, otp, mfaType)
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
                is InvalidOtpException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }
                else ->
                {
                    val responseError = ResponseError("Something went wrong while trying to complete sign-in.")
                    logger.error("Error completing sign-in with.", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}
