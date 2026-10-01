package com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RecertifyInformationRequestAcceptedFactRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/accepted-fact-offers/{factId}/recertifications")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestFactRecertificationResourceOperations
{
    @POST
    fun recertify(
        @PathParam("id") id: String,
        @PathParam("factId") factId: String,
        request: RecertifyInformationRequestAcceptedFactRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
