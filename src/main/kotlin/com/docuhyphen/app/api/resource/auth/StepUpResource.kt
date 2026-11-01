package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.exception.InvalidOtpException
import com.docuhyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.docuhyphen.app.api.exception.OTPExpiredException
import com.docuhyphen.app.api.exception.TooManyRequestsException
import com.docuhyphen.app.api.resource.auth.operations.StepUpResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.StepUpCompleteRequest
import com.docuhyphen.app.api.resource.model.StepUpInitiateRequest
import com.docuhyphen.app.api.resource.model.StepUpOtpRegenerationRequest
import com.docuhyphen.app.api.service.auth.AuthRequestRateLimitedException
import com.docuhyphen.app.api.service.auth.StepUpFlowService
import com.docuhyphen.app.api.service.auth.StepUpMfaRateLimitedException
import com.docuhyphen.app.api.service.auth.StepUpRequestException
import io.quarkus.security.UnauthorizedException
import io.vertx.core.http.HttpServerRequest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class StepUpResource @Inject constructor(
    private val stepUpFlowService: StepUpFlowService,
) : StepUpResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(StepUpResource::class.java)
        private const val TOO_MANY_REQUESTS = 429
    }

    override fun initiate(
        requestId: String?,
        request: HttpServerRequest,
        payload: StepUpInitiateRequest,
    ): Response
    {
        return try
        {
            Response.ok(stepUpFlowService.initiate(payload, request.remoteAddress()?.host(), requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is AuthRequestRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is StepUpRequestException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error initiating step-up authentication", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to initiate step-up authentication"))
                        .build()
                }
            }
        }
    }

    override fun complete(
        requestId: String?,
        request: HttpServerRequest,
        payload: StepUpCompleteRequest,
    ): Response
    {
        return try
        {
            Response.ok(stepUpFlowService.complete(payload, request.remoteAddress()?.host(), requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is AuthRequestRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is StepUpRequestException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is InvalidOtpException,
                is MaxAttemptsOTPExceededException,
                is OTPExpiredException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error completing step-up authentication", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to complete step-up authentication"))
                        .build()
                }
            }
        }
    }

    override fun regenerateOtp(
        requestId: String?,
        request: HttpServerRequest,
        payload: StepUpOtpRegenerationRequest,
    ): Response
    {
        return try
        {
            Response.ok(stepUpFlowService.regenerateOtp(payload, request.remoteAddress()?.host(), requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is AuthRequestRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is StepUpRequestException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is StepUpMfaRateLimitedException ->
                    Response.status(TOO_MANY_REQUESTS)
                        .entity(
                            ResponseError(
                                errorMessage = exception.message,
                                reasonCode = "OTP_RATE_LIMITED",
                                retryAfterSeconds = exception.retryAfterSeconds,
                            )
                        )
                        .build()

                is TooManyRequestsException ->
                    Response.status(TOO_MANY_REQUESTS).entity(ResponseError(exception.message)).build()

                is InvalidOtpException,
                is MaxAttemptsOTPExceededException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error regenerating step-up verification code", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to regenerate step-up verification code"))
                        .build()
                }
            }
        }
    }
}
