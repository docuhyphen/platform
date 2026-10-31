package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.InformationRequestAccessSessionDtoMapper
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestNoAuthAccessResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestAccessSessionStepUpService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthRateLimit
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class InformationRequestNoAuthAccessResource @Inject constructor(
    private val contactProofService: InformationRequestContactProofService,
    private val rateLimit: InformationRequestNoAuthRateLimit,
    private val stepUpService: InformationRequestAccessSessionStepUpService,
    private val requestContext: AuthTokenContext,
) : InformationRequestNoAuthAccessResourceOperations
{
    override fun issueContactProofChallenge(
        accessLinkToken: String?,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1000) {
            try
            {
                rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE, requestContext.clientIp)
                val token = requiredToken(accessLinkToken)
                    ?: return@withFixedFloor missingTokenResponse()
                contactProofService.issueChallenge(token)
                Response.status(NO_CONTENT).build()
            }
            catch (exception: Exception)
            {
                handleException("Information Request contact-proof challenge issuance failed", exception)
            }
        }
    }

    override fun verifyContactProofChallenge(
        accessLinkToken: String?,
        request: VerifyInformationRequestContactProofRequest,
    ): Response
    {
        return try
        {
            rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION, requestContext.clientIp)
            val token = requiredToken(accessLinkToken)
                ?: return missingTokenResponse()
            val session = contactProofService.verifyChallenge(token, request.otp)
            Response.status(CREATED)
                .header("Cache-Control", "no-store")
                .entity(InformationRequestAccessSessionDtoMapper.toDto(session))
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request contact-proof verification failed", exception)
        }
    }

    override fun issueStepUpChallenge(accessLinkToken: String?, sessionToken: String?): Response {
        return ResourceEndpointDelayHelper.withFixedFloor(1000) {
            try {
                rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE, requestContext.clientIp)
                val token = requiredToken(accessLinkToken)
                    ?: return@withFixedFloor missingTokenResponse()
                stepUpService.issueChallenge(token, sessionToken)
                Response.status(NO_CONTENT).build()
            } catch (exception: Exception) {
                handleException("Information Request access session step-up challenge issuance failed", exception)
            }
        }
    }

    override fun verifyStepUp(
        accessLinkToken: String?,
        sessionToken: String?,
        request: VerifyInformationRequestContactProofRequest,
    ): Response {
        return try {
            rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION, requestContext.clientIp)
            val token = requiredToken(accessLinkToken)
                ?: return missingTokenResponse()
            val stepUp = stepUpService.verify(token, sessionToken, request.otp)
            Response.status(CREATED)
                .header("Cache-Control", "no-store")
                .entity(InformationRequestAccessSessionDtoMapper.toDto(stepUp))
                .build()
        } catch (exception: Exception) {
            handleException("Information Request access session step-up verification failed", exception)
        }
    }

    private fun requiredToken(raw: String?): String? = raw?.takeIf { it.isNotBlank() }

    private fun missingTokenResponse(): Response =
        Response.status(BAD_REQUEST)
            .entity(ResponseError("An access link token is required"))
            .build()

    private fun handleException(message: String, exception: Exception): Response =
        when (exception)
        {
            is InformationRequestRateLimitedException -> Response.status(TOO_MANY_REQUESTS)
                .header(RETRY_AFTER_HEADER, exception.retryAfterSeconds)
                .entity(ResponseError(exception.message, exception.reasonCode, exception.retryAfterSeconds)).build()

            is InformationRequestLifecycleException -> InformationRequestCommandHttp.lifecycleRefused(exception)

            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()

            is com.docuhyphen.app.api.exception.InformationRequestCommandRequestException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()

            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(
                    ResponseError(
                        exception.message,
                        com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog.FORBIDDEN
                    )
                ).build()

            is UnauthorizedException -> Response.status(UNAUTHORIZED)
                .entity(ResponseError(exception.message)).build()

            else -> InformationRequestCommandHttp.storageRuleRefused(logger, message, exception) ?: run {
                logger.error(message, exception)
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
            }
        }

    private companion object
    {
        const val RETRY_AFTER_HEADER = "Retry-After"
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthAccessResource::class.java)
    }
}
