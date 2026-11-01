package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.AddOrganizationGroupRequest
import com.docuhyphen.app.api.resource.model.UpdateOrganizationGroupRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationGroupResourceOperations {
    @Path("/{organizationId}/groups")
    @POST
    fun addOrganizationGroup(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        addOrganizationGroupRequest: AddOrganizationGroupRequest,
    ): Response

    @Path("/{organizationId}/groups/{groupId}")
    @PUT
    fun updateOrganizationGroup(
        @PathParam("organizationId") organizationId: String,
        @PathParam("groupId") groupId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        updateOrganizationGroupRequest: UpdateOrganizationGroupRequest,
    ): Response

    @Path("/{organizationId}/groups")
    @GET
    fun getOrganizationGroups(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @DELETE
    @Path("/{organizationId}/groups/{groupId}")
    fun deleteOrganizationGroup(
        @PathParam("organizationId") organizationId: String?,
        @PathParam("groupId") groupId: String?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @POST
    @Path("/{organizationId}/groups/{groupId}/icon")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadOrganizationGroupIcon(
        @RestForm("file") file: File?,
        @RestForm("contentType") contentType: String?,
        @PathParam("organizationId") organizationId: String,
        @PathParam("groupId") groupId: String,
    ): Response

    @DELETE
    @Path("/{organizationId}/groups/{groupId}/icon")
    fun deleteOrganizationGroupIcon(
        @PathParam("organizationId") organizationId: String,
        @PathParam("groupId") groupId: String,
    ): Response
}
