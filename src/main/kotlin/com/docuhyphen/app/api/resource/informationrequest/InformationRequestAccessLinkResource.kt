package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestAccessLinkDtoMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.IssueInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ReplaceInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBootstrapShareLinkIssuance
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.IssueInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.service.informationrequest.ReplaceInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.service.informationrequest.RevokeInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.service.informationrequest.RotateInformationRequestBootstrapShareLinkCommand
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
 * Authenticated, owner-facing REST adapter for a request party's
 * [com.docuhyphen.app.api.model.entity.ShareLinkMode.VERIFICATION_BOOTSTRAP] access link: issuance,
 * rotation, replacement, and revocation. The respondent-facing no-auth adapter that consumes a link
 * to prove contact and mint a session is a separate resource behind its own credential check.
 */
@Path("/information-requests/{id}/access-links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestAccessLinkResource @Inject constructor(
    private val bootstrapShareLinkService: InformationRequestBootstrapShareLinkService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun issue(
        @PathParam("id") id: String,
        request: IssueInformationRequestAccessLinkRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            issued(
                bootstrapShareLinkService.issue(
                    IssueInformationRequestBootstrapShareLinkCommand(
                        requestId = requestId,
                        partyId = request.partyId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                        expiresAt = request.expiresAt,
                        maxUses = request.maxUses,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link issuance failed", exception)
        }
    }

    @POST
    @Path("/{shareLinkId}/rotation")
    fun rotate(
        @PathParam("id") id: String,
        @PathParam("shareLinkId") shareLinkId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val linkId = parseUuid(shareLinkId) ?: return badRequest("Invalid access link id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            rotated(
                bootstrapShareLinkService.rotate(
                    RotateInformationRequestBootstrapShareLinkCommand(
                        requestId = requestId,
                        shareLinkId = linkId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link rotation failed", exception)
        }
    }

    @POST
    @Path("/{shareLinkId}/replacement")
    fun replace(
        @PathParam("id") id: String,
        @PathParam("shareLinkId") shareLinkId: String,
        request: ReplaceInformationRequestAccessLinkRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val linkId = parseUuid(shareLinkId) ?: return badRequest("Invalid access link id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            issued(
                bootstrapShareLinkService.replace(
                    ReplaceInformationRequestBootstrapShareLinkCommand(
                        requestId = requestId,
                        shareLinkId = linkId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                        expiresAt = request.expiresAt,
                        maxUses = request.maxUses,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link replacement failed", exception)
        }
    }

    @POST
    @Path("/{shareLinkId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("shareLinkId") shareLinkId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val linkId = parseUuid(shareLinkId) ?: return badRequest("Invalid access link id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            Response.ok(
                InformationRequestAccessLinkDtoMapper.toDto(
                    bootstrapShareLinkService.revoke(
                        RevokeInformationRequestBootstrapShareLinkCommand(
                            requestId = requestId,
                            shareLinkId = linkId,
                            access = accessContextFactory.currentAuthenticated(),
                            precondition = CommandPreconditionHeader.required(ifMatch),
                            idempotencyKey = commandKey,
                        ),
                    ),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            handleException("Information Request access link revocation failed", exception)
        }
    }

    private fun issued(issuance: InformationRequestBootstrapShareLinkIssuance): Response =
        Response.status(CREATED)
            .entity(InformationRequestAccessLinkDtoMapper.toIssuedDto(issuance))
            .build()

    private fun rotated(issuance: InformationRequestBootstrapShareLinkIssuance): Response =
        Response.ok(InformationRequestAccessLinkDtoMapper.toIssuedDto(issuance)).build()

    private fun requiredIdempotencyKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

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
        val logger = LoggerFactory.getLogger(InformationRequestAccessLinkResource::class.java)
    }
}
