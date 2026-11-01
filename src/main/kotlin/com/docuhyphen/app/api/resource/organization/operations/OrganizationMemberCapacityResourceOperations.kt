package com.docuhyphen.app.api.resource.organization.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface OrganizationMemberCapacityResourceOperations {
    @GET
    @Path("/{orgId}/member-capacity")
    fun getMemberCapacity(@PathParam("orgId") orgId: String): Response
}
