package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.model.InformationRequestAccessSessionDtoMapper
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestNoAuthAccessResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthRateLimit
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

/**
 * No-auth, respondent-facing adapter proving recipient contact ownership of a
 * [com.docuhyphen.app.api.model.entity.ShareLinkMode.VERIFICATION_BOOTSTRAP] access link and minting
 * the [com.docuhyphen.app.api.model.entity.RequestAccessSession] it authorizes. This resource stays
 * outside the global access-token filter (see the `/no-auth/information-requests/` allowlist entry in
 * `EndpointAuthorizationFilter`) and validates the bootstrap token presented in
 * [com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER] itself, the same way
 * [com.docuhyphen.app.api.resource.exchange.NoAuthExchangeResource] validates its own headers. The
 * authenticated, owner-facing issuance/rotation/replacement/revocation of the link itself is a
 * separate resource, [InformationRequestAccessLinkResource].
 */
class InformationRequestNoAuthAccessResource @Inject constructor(
    private val contactProofService: InformationRequestContactProofService,
    private val rateLimit: InformationRequestNoAuthRateLimit,
) : InformationRequestNoAuthAccessResourceOperations
{
    override fun issueContactProofChallenge(
        accessLinkToken: String?,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1000) {
            try
            {
                rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE)
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
            rateLimit.requireWithinLimit(InformationRequestNoAuthAttempt.SESSION)
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
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is InformationRequestLifecycleException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()

            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()

            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(ResponseError(exception.message)).build()

            is UnauthorizedException -> Response.status(UNAUTHORIZED)
                .entity(ResponseError(exception.message)).build()

            else ->
            {
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
