package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RequestBodySerializationExceptionMapperTest
{
    private val mapper = RequestBodySerializationExceptionMapper()

    @Test
    fun `a body missing required fields is refused with the missing field names`()
    {
        val response = mapper.toResponse(decodeFailure("{}"))
        val error = Json.decodeFromString(ResponseError.serializer(), response.entity as String)

        assertEquals(400, response.status)
        assertEquals("The request body is missing required fields: otp", error.errorMessage)
        assertEquals(RequestBodySerializationExceptionMapper.REASON_CODE, error.reasonCode)
    }

    @Test
    fun `a body that is not valid JSON is refused without echoing the input or internal types`()
    {
        val response = mapper.toResponse(decodeFailure("{\"otp\": "))
        val body = response.entity as String
        val error = Json.decodeFromString(ResponseError.serializer(), body)

        assertEquals(400, response.status)
        assertEquals("The request body is not valid JSON for this request", error.errorMessage)
        assertEquals(RequestBodySerializationExceptionMapper.REASON_CODE, error.reasonCode)
        assertFalse(body.contains("com.docuhyphen"))
        assertFalse(body.contains("otp"))
    }

    private fun decodeFailure(body: String): SerializationException =
        runCatching { Json.decodeFromString(VerifyInformationRequestContactProofRequest.serializer(), body) }
            .exceptionOrNull() as SerializationException
}
