package com.docuhyphen.app.api.resource.informationrequest.response.handler

import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.resource.model.InformationRequestResponseFieldValuesPatchRequest
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.*

class InformationRequestResponseRequestHandlerTest {
    private val handler = InformationRequestResponseRequestHandler(mock())

    @Test
    fun `a save within its limits is accepted for the service to judge`() {
        assertNull(handler.invalidPatch(saving(listOf(patch(narrative = "n".repeat(4000), values = 500)))))
    }

    @Test
    fun `too many answers, too long a note or too many Field values in one save are refused before anything runs`() {
        listOf(
            saving(List(201) { patch() }) to "A save names at most 200 answers",
            saving(listOf(patch(narrative = "n".repeat(4001)))) to "A note on an answer is at most 4000 characters",
            saving(listOf(patch(values = 300), patch(values = 201))) to "A save carries at most 500 Field values",
        ).forEach { (request, message) ->
            val refusal = requireNotNull(handler.invalidPatch(request))
            assertEquals(Response.Status.BAD_REQUEST.statusCode, refusal.status)
            assertEquals(message, (refusal.entity as ResponseError).errorMessage)
        }
    }

    private fun saving(patches: List<InformationRequestResponsePatchRequest>) =
        PatchInformationRequestResponsesRequest(patches)

    private fun patch(narrative: String? = null, values: Int = 0) = InformationRequestResponsePatchRequest(
        requirementId = UUID.randomUUID(),
        narrative = narrative,
        fieldValues = if (values == 0) null else InformationRequestResponseFieldValuesPatchRequest(
            values = List(values) { FieldValueEntry(UUID.randomUUID(), JsonPrimitive("value")) },
        ),
    )
}
