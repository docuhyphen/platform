package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.model.dto.OrganizationSettingsDto
import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.UpdateOrganizationRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationResourceOperations {
    @Path("/{organizationId}")
    @PUT
    fun updateOrganization(
        @PathParam("organizationId") organizationId: String?,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        updateOrganizationRequest: UpdateOrganizationRequest,
    ): Response

    @PUT
    @Path("/{organizationId}/settings")
    fun updateOrganizationSettings(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
        settingsDto: OrganizationSettingsDto,
    ): Response
}
