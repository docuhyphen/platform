package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.AppUserExistsException
import com.dochyphen.app.api.exception.ConfirmationPasswordRequiredException
import com.dochyphen.app.api.exception.EmailExistsException
import com.dochyphen.app.api.exception.EmailNotFoundException
import com.dochyphen.app.api.exception.EmailRequiredException
import com.dochyphen.app.api.exception.ExistingSignUpException
import com.dochyphen.app.api.exception.IncorrectSignUpCompletionStatusException
import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.InvalidOtpException
import com.dochyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.dochyphen.app.api.exception.OTPExpiredException
import com.dochyphen.app.api.exception.OtpRequiredException
import com.dochyphen.app.api.exception.PasswordContainsEmailException
import com.dochyphen.app.api.exception.PasswordMismatchException
import com.dochyphen.app.api.exception.PasswordRequiredException
import com.dochyphen.app.api.exception.PasswordRequirementsNotMetException
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.SignUpCompletionRequest
import com.dochyphen.app.api.resource.model.SignUpCompletionResponse
import com.dochyphen.app.api.resource.model.SignUpInitiateRequest
import com.dochyphen.app.api.resource.model.SignUpInitiateResponse
import com.dochyphen.app.api.resource.model.SignUpRegenerationRequest
import com.dochyphen.app.api.service.auth.AuthenticationService
import com.dochyphen.app.api.service.auth.SignUpService
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

@Path("/auth/sign-up")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignUpResource @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val signUpService: SignUpService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignUpResource::class.java)
    }

    @POST
    @Path("/initiation")
    fun initiateSignUp(payload: SignUpInitiateRequest): Response
    {
        return try
        {
            signUpService.initiateSignUp(payload.email.toString())

            val signUpInitiateResponse = SignUpInitiateResponse(message = "Confirmation link sent")
            Response.ok(signUpInitiateResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExistingSignUpException ->
                {
                    val signUpInitiateResponse = SignUpInitiateResponse(message = exception.message)
                    Response.ok(signUpInitiateResponse).build()
                }

                is EmailRequiredException,
                is AppUserExistsException,
                is EmailExistsException,
                is InvalidEmailException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message))
                    .build()

                else ->
                {
                    logger.error("Error initiating sign up", exception)
                    val responseError = ResponseError("A server error occurred while signing up.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }

        }
    }

    @POST
    @Path("/completion")
    fun completeSignUp(signUpRequest: SignUpCompletionRequest): Response
    {
        return try
        {
            with(signUpRequest) {
                signUpService.completeSignUp(email, otp, password, confirmationPassword)
            }

            val signUpCompletionResponse = SignUpCompletionResponse("Sign up successful!")

            Response.ok(signUpCompletionResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailRequiredException,
                is AppUserExistsException,
                is InvalidEmailException,
                is InvalidOtpException,
                is PasswordRequiredException,
                is ConfirmationPasswordRequiredException,
                is PasswordRequirementsNotMetException,
                is PasswordMismatchException,
                is EmailNotFoundException,
                is MaxAttemptsOTPExceededException,
                is IncorrectSignUpCompletionStatusException,
                is OtpRequiredException,
                is PasswordContainsEmailException,
                is OTPExpiredException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error completing sign up", exception)
                    val responseError = ResponseError("A server error occurred while completing sign up.")
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @POST
    @Path("/otp-regeneration")
    fun regenerateOtp(request: SignUpRegenerationRequest): Response
    {
        return try
        {
            signUpService.regenerateOtp(request.email)
            val otpRegenerationResponse =
                SignUpCompletionResponse("OTP regenerated successfully, please check your email for the new OTP.")
            Response.ok(otpRegenerationResponse).build()

        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailRequiredException,
                is EmailNotFoundException,
                is AppUserExistsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error regenerating OTP", exception)
                    val responseError = ResponseError("A server error occurred while regenerating OTP.")
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}