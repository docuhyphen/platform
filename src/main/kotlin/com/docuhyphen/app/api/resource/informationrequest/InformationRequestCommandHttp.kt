package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestSubmissionDtoMapper
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionIncompleteException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateValidationException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateVersionUnavailableException
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.Logger
import java.util.UUID

object InformationRequestCommandHttp
{
    const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    const val ACCESS_LINK_TOKEN_HEADER = "X-Request-Access-Token"
    const val SESSION_TOKEN_HEADER = "X-Request-Session-Token"
    private const val UNPROCESSABLE_CONTENT = 422

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: throw InformationRequestCommandRequestException("Invalid $name")

    fun idempotencyKey(raw: String?): String =
        raw?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InformationRequestCommandRequestException("Idempotency-Key is required")

    fun withNoAuthAccess(
        readAccessService: InformationRequestNoAuthReadAccessService,
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
        call: (UUID, RequestAccessContext) -> Response,
    ): Response
    {
        val requestId = uuid(id, "information request id")
        val token = accessLinkToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InformationRequestCommandRequestException("An access link token is required")
        val noAuthAccess = readAccessService.resolve(token, sessionToken)
        if (noAuthAccess.requestId != requestId)
        {
            return error(NOT_FOUND, "Information Request not found")
        }
        return call(requestId, noAuthAccess.access)
    }

    fun refused(logger: Logger, message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is InformationRequestCommandRequestException -> error(BAD_REQUEST, exception.message)
            is InformationRequestTemplateValidationException -> error(BAD_REQUEST, exception.message)
            is InformationRequestCapabilityNotInstalledException ->
                error(CONFLICT, exception.message, InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED)
            is InformationRequestTemplateVersionUnavailableException -> error(CONFLICT, exception.message, exception.code)
            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> error(CONFLICT, exception.message, exception.reasonCode)
            is InformationRequestSubmissionIncompleteException -> Response.status(UNPROCESSABLE_CONTENT)
                .entity(
                    InformationRequestSubmissionDtoMapper.refusal(
                        exception.message,
                        exception.reasonCode,
                        exception.readiness,
                    ),
                )
                .build()
            is InformationRequestLifecycleException ->
                if (exception.reasonCode == InformationRequestErrorCatalog.NOT_FOUND)
                    error(NOT_FOUND, exception.message, exception.reasonCode)
                else
                    error(CONFLICT, exception.message, exception.reasonCode)
            is IllegalArgumentException -> error(NOT_FOUND, exception.message)
            is IllegalStateException -> error(CONFLICT, exception.message)
            is ForbiddenException -> error(FORBIDDEN, exception.message)
            is UnauthorizedException -> error(UNAUTHORIZED, exception.message)
            else ->
            {
                logger.error(message, exception)
                error(INTERNAL_SERVER_ERROR, "Request failed")
            }
        }
    }

    private fun error(status: Response.Status, message: String?, reasonCode: String? = null): Response =
        Response.status(status).entity(ResponseError(message, reasonCode)).build()
}
