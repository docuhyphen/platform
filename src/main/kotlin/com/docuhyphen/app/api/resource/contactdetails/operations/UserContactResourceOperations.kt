package com.docuhyphen.app.api.resource.contactdetails.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("me/contacts")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface UserContactResourceOperations {
    @GET
    fun searchContacts(
        @QueryParam("query") query: String?,
        @QueryParam("limit") limit: Int?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    @Path("/recent")
    fun recentContacts(
        @QueryParam("limit") limit: Int?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
