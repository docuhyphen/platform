package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.auth.operations.PasswordResetResourceOperations
import com.docuhyphen.app.api.resource.model.PasswordResetCompletionRequest
import com.docuhyphen.app.api.resource.model.PasswordResetInitiationRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.PasswordResetService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

class PasswordResetResource @Inject constructor(
    private val passwordResetService: PasswordResetService,
) : PasswordResetResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PasswordResetResource::class.java)
    }

    override fun initiatePasswordReset(payload: PasswordResetInitiationRequest): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(1500) {
                passwordResetService.initiatePasswordReset(payload.email)
                Response.ok().build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailRequiredException,
                is InvalidEmailException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is EmailNotFoundException -> Response.ok().build()

                else ->
                {
                    logger.error("Error processing password reset request.", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred."))
                        .build()
                }
            }
        }
    }

    override fun completePasswordReset(payload: PasswordResetCompletionRequest): Response
    {
        return try
        {
            ResourceEndpointDelayHelper.withFixedFloor(1500) {
                with(payload) {
                    passwordResetService.completePasswordReset(email, otp, password, confirmationPassword)
                }
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
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error completing password reset.", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred."))
                        .build()
                }
            }
        }
    }
}
