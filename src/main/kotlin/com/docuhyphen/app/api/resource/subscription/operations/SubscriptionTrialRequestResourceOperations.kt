package com.docuhyphen.app.api.resource.subscription.operations

import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestCreateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/subscription-trial-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface SubscriptionTrialRequestResourceOperations {
    @POST
    fun create(request: SubscriptionTrialRequestCreateRequest): Response

    @GET
    @Path("/current")
    fun current(): Response
}
