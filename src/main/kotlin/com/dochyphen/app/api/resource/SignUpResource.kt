package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.*
import com.dochyphen.app.api.resource.model.*
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
        ResourceEndpointDelayHelper.delayEndpoint(3000, 6000)

        return try
        {
            signUpService.initiateSignUp(payload.email.toString().trim().lowercase())

            val signUpInitiateResponse =
                SignUpInitiateResponse(message = "We have sent you a verification code to confirm your email.")
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
        ResourceEndpointDelayHelper.delayEndpoint(1600, 3000)

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
                is OTPExpiredException,
                is OtpMaxRetryLimitReachedException ->
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
        ResourceEndpointDelayHelper.delayEndpoint(1500, 3000)

        return try
        {
            signUpService.regenerateOtp(request.email)
            val otpRegenerationResponse =
                SignUpCompletionResponse("OTP regenerated successfully")
            Response.ok(otpRegenerationResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailRequiredException,
                is OtpRegenerationCooldownException,
                is EmailNotFoundException,
                is AppUserExistsException,
                is OtpMaxRetryLimitReachedException ->
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