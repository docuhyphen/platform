package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.EmailRequiredException
import com.dochyphen.app.api.exception.InvalidOtpException
import com.dochyphen.app.api.exception.InvalidSignInCredentialsException
import com.dochyphen.app.api.exception.OTPExpiredException
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.SignInCompletionRequest
import com.dochyphen.app.api.resource.model.SignInCompletionResponse
import com.dochyphen.app.api.resource.model.SignInRequest
import com.dochyphen.app.api.resource.model.SignInResponse
import com.dochyphen.app.api.service.auth.SignInService
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
            ResourceEndpointDelayHelper.randomDelay(3000, 6000)

            with(payload) {
                signInService.initiateSignIn(payload.email, payload.password)
                val signInResponse = SignInResponse("")
                Response.ok(signInResponse).build()
            }
        }
        catch (exception: Exception)
        {
            when(exception)
            {
                is InvalidSignInCredentialsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }
                else ->
                {
                    Response.status(INTERNAL_SERVER_ERROR).entity(mapOf("error" to exception.message)).build()
                }
            }
        }
    }

    @POST
    @Path("/completion")
    fun completeSignIn(payload: SignInCompletionRequest): Response
    {
        return try
        {
            val signInToken = with(payload) {
                signInService.completeSignIn(email, otp)
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
                    logger.error("Error completing sign-in", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}
