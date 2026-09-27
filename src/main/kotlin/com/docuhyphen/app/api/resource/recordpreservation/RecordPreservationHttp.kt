package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.resource.model.ResponseError
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.Logger
import java.util.UUID

object RecordPreservationHttp
{
    private const val DISPOSAL_REFUSAL = "a record under disposal cannot be placed on hold"

    fun uuid(raw: String, name: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw RecordPreservationRequestException("Invalid $name")

    fun refused(logger: Logger, message: String, exception: Exception): Response
    {
        if (exception is WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception
        return when
        {
            exception is RecordPreservationRequestException -> error(Response.Status.BAD_REQUEST, exception.message)
            exception is RecordPreservationNotFoundException -> error(Response.Status.NOT_FOUND, exception.message)
            exception is RecordPreservationException -> error(Response.Status.CONFLICT, exception.message, exception.reasonCode)
            exception is ForbiddenException -> error(Response.Status.FORBIDDEN, exception.message)
            exception is UnauthorizedException -> error(Response.Status.UNAUTHORIZED, exception.message)
            causedByDisposal(exception) ->
                error(Response.Status.CONFLICT, "A record under disposal cannot be placed on hold", RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS)
            else ->
            {
                logger.error(message, exception)
                error(Response.Status.INTERNAL_SERVER_ERROR, "Request failed")
            }
        }
    }

    private fun causedByDisposal(exception: Throwable): Boolean =
        generateSequence(exception) { it.cause?.takeIf { cause -> cause !== it } }
            .any { it.message.orEmpty().contains(DISPOSAL_REFUSAL) }

    private fun error(status: Response.Status, message: String?, reasonCode: String? = null): Response =
        Response.status(status).entity(ResponseError(message, reasonCode)).build()
}
