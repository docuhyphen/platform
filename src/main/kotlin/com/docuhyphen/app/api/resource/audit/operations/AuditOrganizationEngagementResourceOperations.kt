package com.docuhyphen.app.api.resource.audit.operations

import com.docuhyphen.app.api.model.dto.AuditEngagementCreateRequestDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{organizationId}/audit-engagements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditOrganizationEngagementResourceOperations
{
    @POST
    fun requestOrganizationEngagement(
        @PathParam("organizationId") organizationId: String,
        body: AuditEngagementCreateRequestDto,
    ): Response

    @GET
    fun listOrganizationEngagements(@PathParam("organizationId") organizationId: String): Response

    @POST
    @Path("/{engagementId}/approve")
    fun approveOrganizationEngagement(
        @PathParam("organizationId") organizationId: String,
        @PathParam("engagementId") engagementId: String,
    ): Response

    @POST
    @Path("/{engagementId}/revoke")
    fun revokeOrganizationEngagement(
        @PathParam("organizationId") organizationId: String,
        @PathParam("engagementId") engagementId: String,
    ): Response
}
