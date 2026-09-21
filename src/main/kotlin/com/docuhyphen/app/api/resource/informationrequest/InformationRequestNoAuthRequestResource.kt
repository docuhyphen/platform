package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestGroupOccurrenceDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponseDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponsePatchRequestMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.informationrequest.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.service.informationrequest.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED
import jakarta.ws.rs.core.Response.Status.PRECONDITION_REQUIRED
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("no-auth/information-requests/{id}")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestNoAuthRequestResource @Inject constructor(
    private val readAccessService: InformationRequestNoAuthReadAccessService,
    private val partyQueryService: InformationRequestPartyQueryService,
    private val responseDraftService: InformationRequestResponseDraftService,
    private val occurrenceService: InformationRequestGroupOccurrenceService,
    private val responseWorkspaceService: InformationRequestResponseWorkspaceService,
)
{
    @GET
    fun get(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            Response.ok(responseWorkspaceService.loadRequest(requestId, noAuthAccess.access)).build()
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request lookup failed", exception)
        }
    }

    @GET
    @Path("/parties")
    fun parties(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            val parties = partyQueryService.listForRequest(requestId, noAuthAccess.access)
            Response.ok(parties).build()
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request party list failed", exception)
        }
    }

    @GET
    @Path("/response-workspace")
    fun responseWorkspace(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            Response.ok(responseWorkspaceService.load(requestId, noAuthAccess.access)).build()
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request response workspace lookup failed", exception)
        }
    }

    @PATCH
    @Path("/responses")
    fun patchResponses(
        @PathParam("id") id: String,
        request: PatchInformationRequestResponsesRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.patches.isEmpty()) return badRequest("At least one patch is required")
            if (request.patches.any(InformationRequestResponsePatchRequestMapper::hasConflictingNarrativeOperation))
                return badRequest("A patch cannot both set and clear its narrative")

            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            val patches = request.patches.map(InformationRequestResponsePatchRequestMapper::toDomain)
            val result = responseDraftService.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = requestId,
                    access = noAuthAccess.access,
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = commandKey,
                    patches = patches,
                    confirmedHiddenResponseClears = request.confirmedHiddenResponseClearRequirementIds,
                ),
            )
            ok(result, patches.map { it.requirementId }.toSet())
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request response patch failed", exception)
        }
    }

    @POST
    @Path("/group-occurrences")
    fun addGroupOccurrence(
        @PathParam("id") id: String,
        request: CreateInformationRequestGroupOccurrenceRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.groupKey.isBlank()) return badRequest("A group key is required")
            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            okOccurrences(
                occurrenceService.add(
                    AddInformationRequestGroupOccurrenceCommand(
                        requestId = requestId,
                        access = noAuthAccess.access,
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                        groupKey = request.groupKey,
                        parentOccurrenceId = request.parentOccurrenceId,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request group occurrence add failed", exception)
        }
    }

    @DELETE
    @Path("/group-occurrences/{occurrenceId}")
    fun removeGroupOccurrence(
        @PathParam("id") id: String,
        @PathParam("occurrenceId") occurrenceId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val occurrence = parseUuid(occurrenceId) ?: return badRequest("Invalid group occurrence id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            okOccurrences(
                occurrenceService.remove(
                    RemoveInformationRequestGroupOccurrenceCommand(
                        requestId = requestId,
                        access = noAuthAccess.access,
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                        occurrenceId = occurrence,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request group occurrence remove failed", exception)
        }
    }

    @PATCH
    @Path("/group-occurrences/order")
    fun reorderGroupOccurrences(
        @PathParam("id") id: String,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.groupKey.isBlank()) return badRequest("A group key is required")
            val noAuthAccess = readAccessService.resolve(token, sessionToken)
            if (noAuthAccess.requestId != requestId)
            {
                return notFound()
            }
            okOccurrences(
                occurrenceService.reorder(
                    ReorderInformationRequestGroupOccurrencesCommand(
                        requestId = requestId,
                        access = noAuthAccess.access,
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                        groupKey = request.groupKey,
                        parentOccurrenceId = request.parentOccurrenceId,
                        orderedOccurrenceIds = request.occurrenceIds,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request group occurrence reorder failed", exception)
        }
    }

    private fun ok(result: InformationRequestResponseDraftResult, requirementIds: Set<UUID>): Response =
        Response.ok(
            result.responses
                .filter { it.informationRequestRequirementId in requirementIds }
                .map {
                    InformationRequestResponseDtoMapper.toDto(
                        it,
                        result.requirementsById.getValue(it.informationRequestRequirementId),
                        result.fieldValueProjectionsByRequirementId[it.informationRequestRequirementId],
                    )
                }
                .toTypedArray(),
        ).header("ETag", result.responseETag).build()

    private fun okOccurrences(result: InformationRequestGroupOccurrenceResult): Response =
        Response.ok(result.occurrences.map(InformationRequestGroupOccurrenceDtoMapper::toDto).toTypedArray())
            .header("ETag", result.responseETag)
            .build()

    private fun requiredIdempotencyKey(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun requiredToken(raw: String?): String? = raw?.takeIf { it.isNotBlank() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun missingTokenResponse(): Response =
        Response.status(BAD_REQUEST)
            .entity(ResponseError("An access link token is required"))
            .build()

    private fun notFound(): Response =
        Response.status(NOT_FOUND)
            .entity(ResponseError("Information Request not found"))
            .build()

    private fun handleException(message: String, exception: Exception): Response =
        when (exception)
        {
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is FieldsPreconditionException -> fieldsPreconditionRefused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()
            is InformationRequestLifecycleException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()
            is FieldValidationException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
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

    private fun fieldsPreconditionRefused(exception: FieldsPreconditionException): Response
    {
        val status = when (exception.kind)
        {
            FieldsPreconditionException.Kind.REQUIRED -> PRECONDITION_REQUIRED
            FieldsPreconditionException.Kind.STALE -> PRECONDITION_FAILED
        }
        val builder = Response.status(status).entity(ResponseError(exception.message, exception.reasonCode))
        exception.currentETag?.let { builder.header("ETag", it) }
        return builder.build()
    }

    private companion object
    {
        const val ACCESS_LINK_TOKEN_HEADER = "X-Request-Access-Token"
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthRequestResource::class.java)
    }
}
