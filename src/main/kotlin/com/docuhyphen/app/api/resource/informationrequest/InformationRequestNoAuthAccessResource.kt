package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestAccessSessionDtoMapper
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.NO_CONTENT
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory

/**
 * No-auth, respondent-facing adapter proving recipient contact ownership of a
 * [com.docuhyphen.app.api.model.entity.ShareLinkMode.VERIFICATION_BOOTSTRAP] access link and minting
 * the [com.docuhyphen.app.api.model.entity.RequestAccessSession] it authorizes. This resource stays
 * outside the global access-token filter (see the `/no-auth/information-requests/` allowlist entry in
 * `EndpointAuthorizationFilter`) and validates the bootstrap token presented in
 * [ACCESS_LINK_TOKEN_HEADER] itself, the same way
 * [com.docuhyphen.app.api.resource.exchange.NoAuthExchangeResource] validates its own headers. The
 * authenticated, owner-facing issuance/rotation/replacement/revocation of the link itself is a
 * separate resource, [InformationRequestAccessLinkResource].
 */
@Path("no-auth/information-request-access-links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestNoAuthAccessResource @Inject constructor(
    private val contactProofService: InformationRequestContactProofService,
)
{
    @POST
    @Path("/challenges")
    fun issueContactProofChallenge(
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1000) {
            try
            {
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

    @POST
    @Path("/sessions")
    fun verifyContactProofChallenge(
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        request: VerifyInformationRequestContactProofRequest,
    ): Response
    {
        return try
        {
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
        const val ACCESS_LINK_TOKEN_HEADER = "X-Request-Access-Token"
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthAccessResource::class.java)
    }
}
