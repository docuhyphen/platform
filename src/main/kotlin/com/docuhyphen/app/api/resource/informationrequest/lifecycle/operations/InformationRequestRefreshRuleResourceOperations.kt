package com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRefreshRuleRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/refresh-rules")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestRefreshRuleResourceOperations
{
    @POST
    fun define(
        @PathParam("id") id: String,
        request: DefineInformationRequestRefreshRuleRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{ruleId}/refreshes")
    fun refresh(
        @PathParam("id") id: String,
        @PathParam("ruleId") ruleId: String,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
