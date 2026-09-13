package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestParticipantAccountUpgradeDtoMapper
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpgradeInformationRequestParticipantAccountRequest
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestParticipantAccountUpgradeService
import com.docuhyphen.app.api.model.informationrequest.UpgradeInformationRequestParticipantAccountCommand
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Authenticated resource completing a Participant's verified registration upgrade to their own App
 * User account on one Information Request. The caller's App User identity comes only from the
 * authenticated access token; [UpgradeInformationRequestParticipantAccountRequest.sessionId] is the
 * no-auth [com.docuhyphen.app.api.model.entity.RequestAccessSession] proving contact with the request
 * party being upgraded. A caller authenticated as anything other than an App User is refused before
 * the upgrade service is invoked.
 */
@Path("/information-requests/{id}/participant-account-links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestParticipantAccountLinkResource @Inject constructor(
    private val upgradeService: InformationRequestParticipantAccountUpgradeService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun upgrade(
        @PathParam("id") id: String,
        request: UpgradeInformationRequestParticipantAccountRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val access = accessContextFactory.currentAuthenticated()
            val appUserId = access.principal.takeIf { it.kind == PrincipalKind.USER }?.id
                ?: return forbidden("Only an authenticated App User may complete a registration upgrade")

            val upgrade = upgradeService.upgrade(
                UpgradeInformationRequestParticipantAccountCommand(
                    requestId = requestId,
                    sessionId = request.sessionId,
                    appUserId = appUserId,
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = commandKey,
                    sessionToken = sessionToken,
                ),
            )
            Response.status(CREATED)
                .entity(InformationRequestParticipantAccountUpgradeDtoMapper.toDto(upgrade))
                .build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request participant account upgrade failed", exception)
        }
    }

    private fun requiredIdempotencyKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun forbidden(message: String): Response =
        Response.status(FORBIDDEN).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception

        return when (exception)
        {
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
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
    }

    private companion object
    {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        val logger = LoggerFactory.getLogger(InformationRequestParticipantAccountLinkResource::class.java)
    }
}
