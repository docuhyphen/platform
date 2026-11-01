package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.AddOrganizationAppUserRequest
import com.docuhyphen.app.api.resource.model.UpdateOrganizationAppUserRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationAppUserResourceOperations {
    @Path("/{organizationId}/app-users")
    @POST
    fun addAppUser(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        addOrganizationAppUserRequest: AddOrganizationAppUserRequest,
    ): Response

    @GET
    @Path("/{organizationId}/app-users")
    fun getAppUsers(@PathParam("organizationId") organizationId: String?): Response

    @PUT
    @Path("/{organizationId}/app-users/{appUserId}")
    fun updateAppUser(
        @PathParam("organizationId") organizationId: String?,
        @PathParam("appUserId") appUserId: String?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        updateOrganizationAppUserRequest: UpdateOrganizationAppUserRequest,
    ): Response

    @Path("/{organizationId}/app-users/{appUserId}")
    @DELETE
    fun deleteAppUser(
        @PathParam("organizationId") organizationId: String?,
        @PathParam("appUserId") appUserId: String?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @Path("/{organizationId}/app-users/{appUserId}")
    @GET
    fun doCheck(
        @QueryParam("check") check: String?,
        @PathParam("organizationId") organizationId: String?,
        @PathParam("appUserId") appUserId: String?,
    ): Response
}
