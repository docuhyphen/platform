package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestResponseDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponsePatchRequestMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
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

/**
 * Authenticated, respondent-facing REST adapter for one runtime Information Request's draft
 * responses. Only the occurrences a caller's own patch names are ever projected back, so a shared
 * request never lets one respondent read another party's disposition or narrative through this
 * surface.
 */
@Path("/information-requests/{id}/responses")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestResponseResource @Inject constructor(
    private val responseDraftService: InformationRequestResponseDraftService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @PATCH
    fun patch(
        @PathParam("id") id: String,
        request: PatchInformationRequestResponsesRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.patches.isEmpty()) return badRequest("At least one patch is required")
            if (request.patches.any(InformationRequestResponsePatchRequestMapper::hasConflictingNarrativeOperation))
                return badRequest("A patch cannot both set and clear its narrative")

            val patches = request.patches.map(InformationRequestResponsePatchRequestMapper::toDomain)
            val result = responseDraftService.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = requestId,
                    access = accessContextFactory.currentAuthenticated(),
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
            handleException("Information Request response patch failed", exception)
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

    private fun requiredIdempotencyKey(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
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
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        val logger = LoggerFactory.getLogger(InformationRequestResponseResource::class.java)
    }
}
