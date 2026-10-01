package com.docuhyphen.app.api.resource.informationrequest.review.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-reviews")
@Produces(APPLICATION_JSON)
interface InformationRequestReviewQueueResourceOperations
{
    @GET
    fun queue(): Response
}
