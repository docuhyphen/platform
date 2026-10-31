package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationHoldView
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import com.docuhyphen.app.api.resource.command.CommandIdempotencyKeyHeader
import com.docuhyphen.app.api.resource.command.CommandPreconditionResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationETag
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.Logger
import java.util.*

object RecordPreservationHttp
{
    const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    const val IF_MATCH_HEADER = "If-Match"
    private const val DISPOSAL_REFUSAL = "a record under disposal cannot be placed on hold"

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw RecordPreservationRequestException("Invalid $name")

    fun idempotencyKey(raw: String?): String {
        val key = CommandIdempotencyKeyHeader.stated(raw)
            ?: throw RecordPreservationRequestException("Idempotency-Key is required")
        if (CommandIdempotencyKeyHeader.exceedsLimit(key)) throw RecordPreservationRequestException(
            CommandIdempotencyKeyHeader.TOO_LONG_MESSAGE,
            CommandIdempotencyKeyHeader.INVALID_REASON_CODE,
        )
        return key
    }

    fun refused(logger: Logger, message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception
        return when
        {
            exception is RecordPreservationRequestException -> error(
                Response.Status.BAD_REQUEST,
                exception.message,
                exception.reasonCode
            )
            exception is RecordPreservationNotFoundException -> error(Response.Status.NOT_FOUND, exception.message)
            exception is RecordPreservationException -> error(
                Response.Status.CONFLICT,
                exception.message,
                exception.reasonCode
            )

            exception is CommandPreconditionException -> CommandPreconditionResponse.refused(exception)
            exception is CommandReceiptConflictException -> error(
                Response.Status.CONFLICT,
                exception.message,
                exception.reasonCode
            )

            exception is ForbiddenException -> error(Response.Status.FORBIDDEN, exception.message)
            exception is UnauthorizedException -> error(Response.Status.UNAUTHORIZED, exception.message)
            causedByDisposal(exception) ->
                error(
                    Response.Status.CONFLICT,
                    "A record under disposal cannot be placed on hold",
                    RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS
                )

            else ->
            {
                logger.error(message, exception)
                error(Response.Status.INTERNAL_SERVER_ERROR, "Request failed")
            }
        }
    }

    fun hold(status: Response.Status, view: RecordPreservationHoldView): Response =
        Response.status(status)
            .entity(RecordPreservationDtoMapper.toDto(view))
            .header("ETag", RecordPreservationETag.holdOf(view.hold))
            .build()

    fun schedule(resourceType: String, view: RecordRetentionScheduleView): Response =
        Response.ok(RecordPreservationDtoMapper.toDto(resourceType, view)).header("ETag", view.etag).build()

    private fun causedByDisposal(exception: Throwable): Boolean =
        generateSequence(exception) { it.cause?.takeIf { cause -> cause !== it } }
            .any { it.message.orEmpty().contains(DISPOSAL_REFUSAL) }

    private fun error(status: Response.Status, message: String?, reasonCode: String? = null): Response =
        Response.status(status).entity(ResponseError(message, reasonCode)).build()
}
