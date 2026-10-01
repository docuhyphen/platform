package com.docuhyphen.app.api.resource.informationrequest.oversight.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-operations")
@Produces(APPLICATION_JSON)
interface InformationRequestOperationsResourceOperations
{
    @GET
    @Suppress("LongParameterList")
    fun queue(
        @QueryParam("state") states: List<String>?,
        @QueryParam("exchangeId") exchangeId: String?,
        @QueryParam("search") search: String?,
        @QueryParam("assigneeId") assigneeId: String?,
        @QueryParam("slaStatus") slaStatuses: List<String>?,
        @QueryParam("exception") exceptions: List<String>?,
        @QueryParam("exceptionsOnly") exceptionsOnly: Boolean?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response
}
