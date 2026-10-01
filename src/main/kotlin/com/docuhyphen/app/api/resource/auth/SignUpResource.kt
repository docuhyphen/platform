package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.auth.operations.SignUpResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.auth.SignUpService
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class SignUpResource @Inject constructor(
    private val signUpService: SignUpService,
    private val clientIpResolver: ClientIpResolver,
) : SignUpResourceOperations
{
    override fun initiateSignUp(
        request: HttpServerRequest,
        requestId: String?,
        payload: SignUpInitiateRequest,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1500) {
            try
            {
                signUpService.initiateSignUp(payload.email, clientIpResolver.resolve(request), requestId)
                Response.ok(SignUpInitiateResponse(message = GENERIC_INITIATION_MESSAGE)).build()
            }
            catch (exception: Exception)
            {
                when (exception)
                {
                    is SignUpRateLimitedException -> tooManyRequests(exception)

                    is EmailRequiredException,
                    is DisposableEmailAddressException,
                    is InvalidEmailException -> badRequest(exception)

                    else ->
                    {
                        logger.error("Error initiating sign up", exception)
                        serverError("A server error occurred while signing up.")
                    }
                }
            }
        }
    }

    override fun completeSignUp(
        request: HttpServerRequest,
        requestId: String?,
        signUpRequest: SignUpCompletionRequest,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) {
            try
            {
                with(signUpRequest) {
                    signUpService.completeSignUp(
                        email,
                        otp,
                        password,
                        confirmationPassword,
                        clientIpResolver.resolve(request),
                        requestId,
                    )
                }
                Response.ok(SignUpCompletionResponse("Sign up successful!")).build()
            }
            catch (exception: Exception)
            {
                when (exception)
                {
                    is SubscriptionDenialException ->
                    {
                        logger.error("Subscription denied while completing sign up", exception)
                        throw exception
                    }

                    is SignUpRateLimitedException -> tooManyRequests(exception)

                    is EmailRequiredException,
                    is InvalidEmailException,
                    is OtpRequiredException,
                    is PasswordRequiredException,
                    is ConfirmationPasswordRequiredException,
                    is PasswordRequirementsNotMetException,
                    is PasswordMismatchException,
                    is PasswordContainsEmailException,
                    is SignUpVerificationRejectedException,
                    is SignUpVerificationBusyException,
                    is AppUserExistsException -> badRequest(exception)

                    else ->
                    {
                        logger.error("Error completing sign up", exception)
                        serverError("A server error occurred while completing sign up.")
                    }
                }
            }
        }
    }

    override fun checkEmailConfirmToken(token: String?): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(300) {
            try
            {
                val email = signUpService.peekEmailFromConfirmationToken(token)

                if (email.isNullOrBlank())
                {
                    invalidLink(InvalidSignUpConfirmationTokenException())
                }
                else
                {
                    Response.ok(SignUpEmailConfirmCheckResponse(email = email)).build()
                }
            }
            catch (exception: Exception)
            {
                logger.error("Error checking sign-up confirmation token", exception)
                serverError("A server error occurred while validating the verification link.")
            }
        }
    }

    override fun confirmEmailWithToken(
        request: HttpServerRequest,
        requestId: String?,
        confirmRequest: SignUpEmailConfirmRequest,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) {
            try
            {
                with(confirmRequest) {
                    signUpService.completeSignUpViaToken(
                        token,
                        password,
                        confirmationPassword,
                        clientIpResolver.resolve(request),
                        requestId,
                    )
                }
                Response.ok(SignUpEmailConfirmResponse("Sign up successful!")).build()
            }
            catch (exception: Exception)
            {
                when (exception)
                {
                    is SubscriptionDenialException ->
                    {
                        logger.error("Subscription denied while confirming sign up via token", exception)
                        throw exception
                    }

                    is SignUpRateLimitedException -> tooManyRequests(exception)

                    is InvalidSignUpConfirmationTokenException -> invalidLink(exception)

                    is AppUserExistsException,
                    is PasswordRequiredException,
                    is ConfirmationPasswordRequiredException,
                    is PasswordRequirementsNotMetException,
                    is PasswordMismatchException,
                    is PasswordContainsEmailException -> badRequest(exception)

                    else ->
                    {
                        logger.error("Error confirming sign up via token", exception)
                        serverError("A server error occurred while completing sign up.")
                    }
                }
            }
        }
    }

    override fun regenerateOtp(
        request: HttpServerRequest,
        requestId: String?,
        regenerationRequest: SignUpRegenerationRequest,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) {
            try
            {
                signUpService.regenerateOtp(regenerationRequest.email, clientIpResolver.resolve(request), requestId)
                Response.ok(SignUpCompletionResponse(GENERIC_REGENERATION_MESSAGE)).build()
            }
            catch (exception: Exception)
            {
                when (exception)
                {
                    is SignUpRateLimitedException -> tooManyRequests(exception)

                    is SignUpResendCooldownException ->
                        Response.status(TOO_MANY_REQUESTS)
                            .header(RETRY_AFTER_HEADER, exception.retryAfterSeconds)
                            .entity(
                                ResponseError(
                                    errorMessage = exception.message,
                                    reasonCode = RESEND_COOLDOWN_REASON_CODE,
                                    retryAfterSeconds = exception.retryAfterSeconds,
                                )
                            )
                            .build()

                    is EmailRequiredException,
                    is InvalidEmailException -> badRequest(exception)

                    else ->
                    {
                        logger.error("Error regenerating OTP", exception)
                        serverError("A server error occurred while regenerating OTP.")
                    }
                }
            }
        }
    }

    private fun badRequest(exception: Exception): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

    private fun invalidLink(exception: InvalidSignUpConfirmationTokenException): Response =
        Response.status(NOT_FOUND)
            .entity(ResponseError(errorMessage = exception.message, reasonCode = INVALID_LINK_REASON_CODE))
            .build()

    private fun tooManyRequests(exception: Exception): Response =
        Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

    private fun serverError(message: String): Response =
        Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(message)).build()

    private companion object
    {
        val logger = LoggerFactory.getLogger(SignUpResource::class.java)
        const val GENERIC_INITIATION_MESSAGE = "If the email is eligible, we've sent a verification code."
        const val GENERIC_REGENERATION_MESSAGE = "If verification is pending for this email, a new code has been sent."
        const val TOO_MANY_REQUESTS = 429
        const val RETRY_AFTER_HEADER = "Retry-After"
        const val INVALID_LINK_REASON_CODE = "SIGN_UP_LINK_INVALID"
        const val RESEND_COOLDOWN_REASON_CODE = "OTP_RATE_LIMITED"
    }
}
