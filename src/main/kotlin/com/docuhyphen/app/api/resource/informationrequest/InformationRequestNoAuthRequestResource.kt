package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestVerificationRequiredException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestHiddenResponseClearDtoMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.occurrence.handler.InformationRequestGroupOccurrenceRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.operations.InformationRequestNoAuthRequestResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.response.handler.InformationRequestResponseRequestHandler
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestNoAuthRequestResource @Inject constructor(
    private val readAccessService: InformationRequestNoAuthReadAccessService,
    private val partyQueryService: InformationRequestPartyQueryService,
    responseDraftService: InformationRequestResponseDraftService,
    occurrenceService: InformationRequestGroupOccurrenceService,
    private val responseWorkspaceService: InformationRequestResponseWorkspaceService,
) : InformationRequestNoAuthRequestResourceOperations
{
    private val responseHandler = InformationRequestResponseRequestHandler(responseDraftService)
    private val occurrences = InformationRequestGroupOccurrenceRequestHandler(occurrenceService)

    override fun get(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val access = readAccessService.resolve(requestId, token, sessionToken)
            val request = readAccessService.readCurrent(requestId, access) { current ->
                responseWorkspaceService.loadRequest(requestId, current)
            }
            Response.ok(request).build()
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request lookup failed", exception)
        }
    }

    override fun parties(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val access = readAccessService.resolve(requestId, token, sessionToken)
            val parties = readAccessService.readCurrent(requestId, access) { current ->
                partyQueryService.listForManagement(requestId, current)
            }
            Response.ok(parties.parties.toTypedArray()).header("ETag", parties.partiesETag).build()
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request party list failed", exception)
        }
    }

    override fun responseWorkspace(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val access = readAccessService.resolve(requestId, token, sessionToken)
            val workspace = readAccessService.readCurrent(requestId, access) { current ->
                responseWorkspaceService.load(requestId, current)
            }
            Response.ok(workspace).build()
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request response workspace lookup failed", exception)
        }
    }

    override fun patchResponses(
        id: String,
        request: PatchInformationRequestResponsesRequest,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            responseHandler.invalidPatch(request)?.let { return it }

            val access = readAccessService.resolve(requestId, token, sessionToken)
            responseHandler.patch(requestId, request, access, ifMatch, commandKey)
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request response patch failed", exception)
        }
    }

    override fun addGroupOccurrence(
        id: String,
        request: CreateInformationRequestGroupOccurrenceRequest,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.groupKey.isBlank()) return badRequest("A group key is required")
            val access = readAccessService.resolve(requestId, token, sessionToken)
            occurrences.add(requestId, request, access, ifMatch, commandKey)
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request group occurrence add failed", exception)
        }
    }

    override fun removeGroupOccurrence(
        id: String,
        occurrenceId: String,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val occurrence = parseUuid(occurrenceId) ?: return badRequest("Invalid group occurrence id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            val access = readAccessService.resolve(requestId, token, sessionToken)
            occurrences.remove(requestId, occurrence, access, ifMatch, commandKey)
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request group occurrence remove failed", exception)
        }
    }

    override fun reorderGroupOccurrences(
        id: String,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val token = requiredToken(accessLinkToken) ?: return missingTokenResponse()
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            if (request.groupKey.isBlank()) return badRequest("A group key is required")
            val access = readAccessService.resolve(requestId, token, sessionToken)
            occurrences.reorder(requestId, request, access, ifMatch, commandKey)
        }
        catch (exception: Exception)
        {
            handleException("No-auth Information Request group occurrence reorder failed", exception)
        }
    }

    private fun requiredIdempotencyKey(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let(InformationRequestCommandHttp::idempotencyKey)

    private fun requiredToken(raw: String?): String? = raw?.takeIf { it.isNotBlank() }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    private fun badRequest(message: String): Response =
        Response.status(BAD_REQUEST).entity(ResponseError(message)).build()

    private fun missingTokenResponse(): Response =
        Response.status(BAD_REQUEST)
            .entity(ResponseError("An access link token is required"))
            .build()

    private fun handleException(message: String, exception: Exception): Response {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is FieldsPreconditionException -> InformationRequestCommandHttp.fieldsPreconditionRefused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is InformationRequestHiddenResponseClearRequiredException -> Response.status(CONFLICT)
                .entity(InformationRequestHiddenResponseClearDtoMapper.toErrorDto(exception)).build()

            is InformationRequestItemRefusalException -> responseHandler.refused(exception)

            is InformationRequestLifecycleException -> InformationRequestCommandHttp.lifecycleRefused(exception)

            is FieldValidationException -> InformationRequestCommandHttp.fieldValueRefused(exception)

            is IllegalStateException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message)).build()

            is com.docuhyphen.app.api.exception.InformationRequestCommandRequestException -> Response.status(BAD_REQUEST)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is IllegalArgumentException -> Response.status(NOT_FOUND)
                .entity(ResponseError(exception.message)).build()

            is InformationRequestVerificationRequiredException -> Response.status(FORBIDDEN)
                .entity(ResponseError(exception.message, InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED)).build()

            is ForbiddenException -> Response.status(FORBIDDEN)
                .entity(
                    ResponseError(
                        exception.message,
                        InformationRequestErrorCatalog.FORBIDDEN
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
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthRequestResource::class.java)
    }
}
