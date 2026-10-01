package com.docuhyphen.app.api.resource.informationrequest.noauth.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.UpgradeInformationRequestParticipantAccountRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/participant-account-links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestParticipantAccountLinkResourceOperations
{
    @POST
    fun upgrade(
        @PathParam("id") id: String,
        request: UpgradeInformationRequestParticipantAccountRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
}
