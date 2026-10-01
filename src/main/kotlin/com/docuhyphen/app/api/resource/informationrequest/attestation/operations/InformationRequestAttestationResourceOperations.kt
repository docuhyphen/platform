package com.docuhyphen.app.api.resource.informationrequest.attestation.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/requirements/{requirementId}/attestations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestAttestationResourceOperations
{
    @POST
    fun record(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        request: RecordInformationRequestAttestationRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
