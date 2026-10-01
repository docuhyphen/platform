package com.docuhyphen.app.api.resource.informationrequest.record.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RecordInformationRequestGeneratedOutputRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/generated-outputs")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestGeneratedOutputResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun record(
        @PathParam("id") id: String,
        request: RecordInformationRequestGeneratedOutputRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
