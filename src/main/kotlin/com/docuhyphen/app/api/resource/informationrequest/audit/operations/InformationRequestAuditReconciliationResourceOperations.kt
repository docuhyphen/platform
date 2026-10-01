package com.docuhyphen.app.api.resource.informationrequest.audit.operations

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/audit-reconciliation")
@Produces(APPLICATION_JSON)
interface InformationRequestAuditReconciliationResourceOperations
{
    @GET
    fun get(@PathParam("id") id: String): Response
}
