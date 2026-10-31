package com.docuhyphen.app.api.resource.informationrequest.occurrence

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestItemRefusalDtoMapper
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.occurrence.handler.InformationRequestGroupOccurrenceRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.occurrence.operations.InformationRequestGroupOccurrenceResourceOperations
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
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
    occurrenceService: InformationRequestGroupOccurrenceService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestGroupOccurrenceResourceOperations
{
    private val occurrences = InformationRequestGroupOccurrenceRequestHandler(occurrenceService)

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
            occurrences.add(requestId, request, accessContextFactory.currentAuthenticated(), ifMatch, commandKey)
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
            occurrences.remove(requestId, occurrence, accessContextFactory.currentAuthenticated(), ifMatch, commandKey)
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
            occurrences.reorder(requestId, request, accessContextFactory.currentAuthenticated(), ifMatch, commandKey)
        }
        catch (exception: Exception)
        {
            handleException("Information Request group occurrence reorder failed", exception)
        }
    }

    private fun requiredIdempotencyKey(raw: String?): String? =
        CommandIdempotencyKeyHeader.stated(raw)?.let(InformationRequestCommandHttp::idempotencyKey)

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

            is InformationRequestItemRefusalException -> Response.status(CONFLICT)
                .entity(InformationRequestItemRefusalDtoMapper.toDto(exception)).build()

            is InformationRequestLifecycleException -> InformationRequestCommandHttp.lifecycleRefused(exception)

            is FieldValidationException -> InformationRequestCommandHttp.fieldValueRefused(exception)
            is FieldsPreconditionException -> InformationRequestCommandHttp.fieldsPreconditionRefused(exception)

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
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestGroupOccurrenceResource::class.java)
    }
}
