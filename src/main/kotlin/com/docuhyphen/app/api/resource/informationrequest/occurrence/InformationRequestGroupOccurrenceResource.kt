package com.docuhyphen.app.api.resource.informationrequest.occurrence

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestGroupOccurrenceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.occurrence.operations.InformationRequestGroupOccurrenceResourceOperations
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestGroupOccurrenceResource @Inject constructor(
    private val occurrenceService: InformationRequestGroupOccurrenceService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestGroupOccurrenceResourceOperations
{
    override fun add(
        id: String,
        request: CreateInformationRequestGroupOccurrenceRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.groupKey.isBlank()) return badRequest("A group key is required")
            ok(
                occurrenceService.add(
                    AddInformationRequestGroupOccurrenceCommand(
                        requestId = requestId,
                        access = accessContextFactory.currentAuthenticated(),
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
            handleException("Information Request group occurrence add failed", exception)
        }
    }

    override fun remove(
        id: String,
        occurrenceId: String,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val occurrence = parseUuid(occurrenceId) ?: return badRequest("Invalid group occurrence id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            ok(
                occurrenceService.remove(
                    RemoveInformationRequestGroupOccurrenceCommand(
                        requestId = requestId,
                        access = accessContextFactory.currentAuthenticated(),
                        precondition = CommandPreconditionHeader.required(ifMatch),
                        idempotencyKey = commandKey,
                        occurrenceId = occurrence,
                    ),
                ),
            )
        }
        catch (exception: Exception)
        {
            handleException("Information Request group occurrence remove failed", exception)
        }
    }

    override fun reorder(
        id: String,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.groupKey.isBlank()) return badRequest("A group key is required")
            ok(
                occurrenceService.reorder(
                    ReorderInformationRequestGroupOccurrencesCommand(
                        requestId = requestId,
                        access = accessContextFactory.currentAuthenticated(),
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
            handleException("Information Request group occurrence reorder failed", exception)
        }
    }

    private fun ok(result: InformationRequestGroupOccurrenceResult): Response =
        Response.ok(result.occurrences.map(InformationRequestGroupOccurrenceDtoMapper::toDto).toTypedArray())
            .header("ETag", result.responseETag)
            .build()

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
        val logger = LoggerFactory.getLogger(InformationRequestGroupOccurrenceResource::class.java)
    }
}
