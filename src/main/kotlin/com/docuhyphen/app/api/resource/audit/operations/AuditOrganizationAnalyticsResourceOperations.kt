package com.docuhyphen.app.api.resource.audit.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{organizationId}/audit-analytics/reconciliation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditOrganizationAnalyticsResourceOperations
{
    @GET
    fun getOrganizationAnalyticsReconciliation(@PathParam("organizationId") organizationId: String): Response
}
