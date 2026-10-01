@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.resource.model.ResponseError
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Provider
class RequestBodySerializationExceptionMapper : ExceptionMapper<SerializationException>
{
    override fun toResponse(exception: SerializationException): Response =
        Response
            .status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                Json.encodeToString(
                    ResponseError.serializer(),
                    ResponseError(errorMessage = messageFor(exception), reasonCode = REASON_CODE),
                )
            )
            .build()

    private fun messageFor(exception: SerializationException): String =
        if (exception is MissingFieldException && exception.missingFields.isNotEmpty())
            "The request body is missing required fields: ${exception.missingFields.joinToString(", ")}"
        else
            "The request body is not valid JSON for this request"

    companion object
    {
        const val REASON_CODE = "REQUEST_BODY_INVALID"
    }
}
