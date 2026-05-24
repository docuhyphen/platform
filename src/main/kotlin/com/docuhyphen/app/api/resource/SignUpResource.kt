package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.SignUpService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import org.slf4j.LoggerFactory

@Path("/auth/sign-up")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignUpResource @Inject constructor(
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

        val genericInitiationMessage = "If the email is eligible, we've sent a verification code."

        return try
        {
            signUpService.initiateSignUp(payload.email)

            val signUpInitiateResponse =
                SignUpInitiateResponse(message = genericInitiationMessage)
            Response.ok(signUpInitiateResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExistingSignUpException ->
                {
                    val signUpInitiateResponse = SignUpInitiateResponse(message = genericInitiationMessage)
                    Response.ok(signUpInitiateResponse).build()
                }

                is AppUserExistsException,
                is EmailExistsException ->
                {
                    val signUpInitiateResponse = SignUpInitiateResponse(message = genericInitiationMessage)
                    Response.ok(signUpInitiateResponse).build()
                }

                is EmailRequiredException,
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

    /**
     * Introspect a verification-link token without consuming it.
     *
     * The frontend hits this on /sign-up/email-confirm page load so it can
     * display "Verifying you@example.com" before the user submits a password
     * — and so an invalid/expired link surfaces immediately instead of after
     * a wasted password entry.
     *
     * Returns 404 for any reason the token can't be resolved (missing, expired,
     * malformed) — never leaks the distinction.
     */
    @GET
    @Path("/email-confirm/{token}")
    fun checkEmailConfirmToken(@PathParam("token") token: String?): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val email = signUpService.peekEmailFromConfirmationToken(token)

            if (email.isNullOrBlank())
            {
                val responseError = ResponseError("This verification link is invalid or has expired.")
                Response.status(NOT_FOUND).entity(responseError).build()
            }
            else
            {
                Response.ok(SignUpEmailConfirmCheckResponse(email = email)).build()
            }
        }
        catch (exception: Exception)
        {
            logger.error("Error checking sign-up confirmation token", exception)
            val responseError = ResponseError("A server error occurred while validating the verification link.")
            Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }

    /**
     * Complete sign-up via the opaque-token flow (user clicked the email link).
     * No OTP, email, or other PII is required from the client — the token alone
     * resolves to the verified email address, and the user just supplies their
     * desired password.
     *
     * The token is consumed atomically on successful resolution, so a leaked
     * URL can't be replayed after the first valid use.
     */
    @POST
    @Path("/email-confirm")
    fun confirmEmailWithToken(request: SignUpEmailConfirmRequest): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1500, 3000)

        return try
        {
            with(request) {
                signUpService.completeSignUpViaToken(token, password, confirmationPassword)
            }
            Response.ok(SignUpEmailConfirmResponse("Sign up successful!")).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is InvalidSignUpConfirmationTokenException ->
                {
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is AppUserExistsException,
                is InvalidEmailException,
                is PasswordRequiredException,
                is ConfirmationPasswordRequiredException,
                is PasswordRequirementsNotMetException,
                is PasswordMismatchException,
                is PasswordContainsEmailException,
                is EmailNotFoundException,
                is OTPExpiredException ->
                {
                    Response.status(BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                else ->
                {
                    logger.error("Error confirming sign up via token", exception)
                    val responseError = ResponseError("A server error occurred while completing sign up.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/otp-regeneration")
    fun regenerateOtp(request: SignUpRegenerationRequest): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1500, 3000)

        val genericRegenerationMessage = "If verification is pending for this email, a new code has been sent."

        return try
        {
            signUpService.regenerateOtp(request.email)
            val otpRegenerationResponse =
                SignUpCompletionResponse(genericRegenerationMessage)
            Response.ok(otpRegenerationResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is EmailNotFoundException,
                is AppUserExistsException ->
                {
                    val otpRegenerationResponse = SignUpCompletionResponse(genericRegenerationMessage)
                    Response.ok(otpRegenerationResponse).build()
                }

                is EmailRequiredException,
                is OtpRegenerationCooldownException,
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