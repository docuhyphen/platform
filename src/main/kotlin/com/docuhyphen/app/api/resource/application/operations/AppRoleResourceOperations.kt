package com.docuhyphen.app.api.resource.application.operations

import com.docuhyphen.app.api.resource.model.GrantAppAdminRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/admin/roles")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface AppRoleResourceOperations
{
    @GET
    @Path("/app-admins")
    fun listAppAdmins(): Response

    @POST
    @Path("/app-admins")
    fun grantAppAdmin(request: GrantAppAdminRequest): Response

    @DELETE
    @Path("/app-admins/{assignmentId}")
    fun revokeAppAdmin(@PathParam("assignmentId") assignmentId: String): Response

    @GET
    @Path("/app-admin-candidates")
    fun searchAppAdminCandidates(
        @QueryParam("q") q: String?,
        @QueryParam("limit") @DefaultValue("20") limit: Int,
    ): Response
}
