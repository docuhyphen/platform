package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.model.PasswordResetCompletionRequest
import com.docuhyphen.app.api.resource.model.PasswordResetInitiationRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.PasswordResetService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
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
        return ResourceEndpointDelayHelper.withFixedFloor(1500) { try
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
    }

    @POST
    @Path("/completion")
    fun completePasswordReset(payload: PasswordResetCompletionRequest): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1500) { try
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
}
