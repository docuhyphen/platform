package com.docuhyphen.app.api.resource.exchange.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations/{targetOrganizationId}/published-exchange-groups")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PublishedExchangeGroupResourceOperations {
    @GET
    fun list(@PathParam("targetOrganizationId") targetOrganizationId: String): Response
}
