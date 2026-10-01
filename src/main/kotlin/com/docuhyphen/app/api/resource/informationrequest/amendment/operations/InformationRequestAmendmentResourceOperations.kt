package com.docuhyphen.app.api.resource.informationrequest.amendment.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.AmendInformationRequestRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/amendments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestAmendmentResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun amend(
        @PathParam("id") id: String,
        request: AmendInformationRequestRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
