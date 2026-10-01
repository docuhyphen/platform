package com.docuhyphen.app.api.resource.informationrequest.parent.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/exchanges/{exchangeId}/information-requests")
@Produces(APPLICATION_JSON)
interface InformationRequestExchangeListingResourceOperations
{
    @GET
    fun list(@PathParam("exchangeId") exchangeId: String): Response
}
