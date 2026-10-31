package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.InformationRequestVerificationRequiredException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestItemRefusalDtoMapper
import com.docuhyphen.app.api.model.InformationRequestSubmissionDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.informationrequest.template.InformationRequestTemplateRefusalResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.execution.RequestExecutionUsageExhaustedException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionIncompleteException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.Logger
import java.sql.SQLException
import java.util.*

object InformationRequestCommandHttp
{
    const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    const val ACCESS_LINK_TOKEN_HEADER = "X-Request-Access-Token"
    const val SESSION_TOKEN_HEADER = "X-Request-Session-Token"
    private const val RETRY_AFTER_HEADER = "Retry-After"
    private const val UNPROCESSABLE_CONTENT = 422

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull()
            ?: throw InformationRequestCommandRequestException("Invalid $name")

    fun idempotencyKey(raw: String?): String {
        val key = CommandIdempotencyKeyHeader.stated(raw)
            ?: throw InformationRequestCommandRequestException("Idempotency-Key is required")
        if (CommandIdempotencyKeyHeader.exceedsLimit(key)) throw InformationRequestCommandRequestException(
            CommandIdempotencyKeyHeader.TOO_LONG_MESSAGE,
            CommandIdempotencyKeyHeader.INVALID_REASON_CODE,
        )
        return key
    }

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
        return call(requestId, readAccessService.resolve(requestId, token, sessionToken))
    }

    fun withNoAuthRead(
        readAccessService: InformationRequestNoAuthReadAccessService,
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
        read: (UUID, RequestAccessContext) -> Response,
    ): Response = withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
        readAccessService.readCurrent(requestId, access) { current -> read(requestId, current) }
    }

    fun refused(logger: Logger, message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception

        return when (exception)
        {
            is InformationRequestCommandRequestException -> error(BAD_REQUEST, exception.message, exception.reasonCode)
            is InformationRequestTemplateValidationException -> InformationRequestTemplateRefusalResponse.of(exception)
            is InformationRequestCapabilityNotInstalledException ->
                error(CONFLICT, exception.message, InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED)

            is InformationRequestTemplateVersionUnavailableException -> error(
                CONFLICT,
                exception.message,
                exception.code
            )

            is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            is CommandReceiptConflictException -> error(CONFLICT, exception.message, exception.reasonCode)
            is InformationRequestRateLimitedException -> Response.status(TOO_MANY_REQUESTS)
                .header(RETRY_AFTER_HEADER, exception.retryAfterSeconds)
                .entity(ResponseError(exception.message, exception.reasonCode, exception.retryAfterSeconds))
                .build()

            is RequestExecutionUsageExhaustedException -> error(
                CONFLICT,
                exception.refusalMessage,
                InformationRequestErrorCatalog.CAPACITY_EXHAUSTED,
            )

            is InformationRequestSubmissionIncompleteException -> Response.status(UNPROCESSABLE_CONTENT)
                .entity(
                    InformationRequestSubmissionDtoMapper.refusal(
                        exception.message,
                        exception.reasonCode,
                        exception.readiness,
                    ),
                )
                .build()

            is InformationRequestItemRefusalException -> Response.status(CONFLICT)
                .entity(InformationRequestItemRefusalDtoMapper.toDto(exception))
                .build()

            is com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException ->
                Response.status(CONFLICT).entity(
                    com.docuhyphen.app.api.model.InformationRequestHiddenResponseClearDtoMapper.toErrorDto(exception)
                ).build()

            is InformationRequestLifecycleException -> lifecycleRefused(exception)

            is FieldValidationException -> fieldValueRefused(exception)
            is FieldsPreconditionException -> fieldsPreconditionRefused(exception)
            is IllegalArgumentException -> error(NOT_FOUND, exception.message)
            is IllegalStateException -> error(CONFLICT, exception.message)
            is InformationRequestVerificationRequiredException ->
                error(FORBIDDEN, exception.message, InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED)

            is ForbiddenException -> error(FORBIDDEN, exception.message, InformationRequestErrorCatalog.FORBIDDEN)
            is UnauthorizedException -> error(UNAUTHORIZED, exception.message)
            else -> storageRuleRefused(logger, message, exception) ?: run {
                logger.error(message, exception)
                error(INTERNAL_SERVER_ERROR, "Request failed")
            }
        }
    }

    fun lifecycleRefused(exception: InformationRequestLifecycleException): Response =
        error(
            if (exception.reasonCode == InformationRequestErrorCatalog.NOT_FOUND) NOT_FOUND else CONFLICT,
            exception.message,
            exception.reasonCode,
        )

    fun fieldValueRefused(exception: FieldValidationException): Response =
        error(BAD_REQUEST, exception.message, InformationRequestErrorCatalog.FIELD_VALUE_INVALID)

    fun fieldsPreconditionRefused(exception: FieldsPreconditionException): Response {
        val status = when (exception.kind) {
            FieldsPreconditionException.Kind.REQUIRED -> PRECONDITION_REQUIRED
            FieldsPreconditionException.Kind.STALE -> PRECONDITION_FAILED
        }
        val builder = Response.status(status).entity(ResponseError(exception.message, exception.reasonCode))
        exception.currentETag?.let { builder.header("ETag", it) }
        return builder.build()
    }

    fun storageRuleRefused(logger: Logger, message: String, exception: Exception): Response? {
        generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<SQLException>()
            .firstOrNull { refusedByStoredRule(it.sqlState) } ?: return null
        logger.warn(message, exception)
        return error(
            CONFLICT,
            "This change breaks a rule the stored information must keep, so nothing was saved",
            InformationRequestErrorCatalog.CONSTRAINT_CONFLICT,
        )
    }

    private fun refusedByStoredRule(sqlState: String?): Boolean =
        sqlState != null && (
                sqlState.startsWith(INTEGRITY_CONSTRAINT_STATE_CLASS) ||
                        sqlState.startsWith(DATA_EXCEPTION_STATE_CLASS) ||
                        sqlState == RAISED_EXCEPTION_STATE
                )

    private fun error(status: Response.Status, message: String?, reasonCode: String? = null): Response =
        Response.status(status).entity(ResponseError(message, reasonCode)).build()

    private const val INTEGRITY_CONSTRAINT_STATE_CLASS = "23"
    private const val DATA_EXCEPTION_STATE_CLASS = "22"
    private const val RAISED_EXCEPTION_STATE = "P0001"
}
