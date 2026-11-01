package com.docuhyphen.app.api.resource.subscription.operations

import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestDecisionRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/platform/subscription-trial-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PlatformSubscriptionTrialRequestResourceOperations {
    @GET
    fun list(
        @QueryParam("status") status: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response

    @PATCH
    @Path("/{requestId}/status")
    fun decide(
        @PathParam("requestId") requestId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) approvalRequestId: String?,
        request: SubscriptionTrialRequestDecisionRequest,
    ): Response
}
