package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.ConfirmationPasswordRequiredException
import com.dochyphen.app.api.exception.EmailNotFoundException
import com.dochyphen.app.api.exception.EmailRequiredException
import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.InvalidOtpException
import com.dochyphen.app.api.exception.OTPExpiredException
import com.dochyphen.app.api.exception.OtpRequiredException
import com.dochyphen.app.api.exception.PasswordMismatchException
import com.dochyphen.app.api.exception.PasswordRequiredException
import com.dochyphen.app.api.exception.PasswordRequirementsNotMetException
import com.dochyphen.app.api.resource.model.PasswordResetCompletionRequest
import com.dochyphen.app.api.resource.model.PasswordResetInitiationRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.auth.PasswordResetService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import org.slf4j.LoggerFactory

@Path("/auth/password-reset")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class PasswordResetResource @Inject constructor(
    private val passwordResetService: PasswordResetService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PasswordResetResource::class.java)
    }

    @POST
    @Path("/initiation")
    fun initiatePasswordReset(payload: PasswordResetInitiationRequest): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(3000, 6000)

        return try
        {
            with(payload) {
                passwordResetService.initiatePasswordReset(email)
                Response.ok().build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailRequiredException,
                is InvalidEmailException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                is EmailNotFoundException ->
                {
                    Response.ok().build()
                }

                else ->
                {
                    logger.error("Error processing password reset request.", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred."))
                        .build()
                }
            }
        }
    }

    @POST
    @Path("/completion")
    fun completePasswordReset(payload: PasswordResetCompletionRequest): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(3000, 6000)

        return try
        {
            with(payload) {
                passwordResetService.completePasswordReset(email, otp, password, confirmationPassword)
                Response.ok().build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailRequiredException,
                is InvalidEmailException,
                is OtpRequiredException,
                is PasswordRequiredException,
                is ConfirmationPasswordRequiredException,
                is PasswordMismatchException,
                is PasswordRequirementsNotMetException,
                is InvalidOtpException,
                is OTPExpiredException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error completing password reset.", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred."))
                        .build()
                }
            }
        }
    }
}
