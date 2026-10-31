package com.docuhyphen.app.api.resource.informationrequest.response

import com.docuhyphen.app.api.exception.InformationRequestVerificationRequiredException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestHiddenResponseClearDtoMapper
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.response.handler.InformationRequestResponseRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.response.operations.InformationRequestResponseResourceOperations
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestResponseResource @Inject constructor(
    responseDraftService: InformationRequestResponseDraftService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestResponseResourceOperations
{
    private val handler = InformationRequestResponseRequestHandler(responseDraftService)

    override fun patch(
        id: String,
        request: PatchInformationRequestResponsesRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = parseUuid(id) ?: return badRequest("Invalid information request id")
            val commandKey = requiredIdempotencyKey(idempotencyKey)
                ?: return badRequest("Idempotency-Key is required")
            handler.invalidPatch(request)?.let { return it }
            handler.patch(requestId, request, accessContextFactory.currentAuthenticated(), ifMatch, commandKey)
        }
        catch (exception: Exception)
        {
            handleException("Information Request response patch failed", exception)
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
            is FieldsPreconditionException -> InformationRequestCommandHttp.fieldsPreconditionRefused(exception)
            is CommandReceiptConflictException -> Response.status(CONFLICT)
                .entity(ResponseError(exception.message, exception.reasonCode)).build()

            is InformationRequestHiddenResponseClearRequiredException -> Response.status(CONFLICT)
                .entity(InformationRequestHiddenResponseClearDtoMapper.toErrorDto(exception)).build()

            is InformationRequestItemRefusalException -> handler.refused(exception)

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
        val logger = LoggerFactory.getLogger(InformationRequestResponseResource::class.java)
    }
}
