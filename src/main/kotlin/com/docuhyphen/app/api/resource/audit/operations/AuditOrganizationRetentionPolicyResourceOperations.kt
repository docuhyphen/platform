package com.docuhyphen.app.api.resource.audit.operations

import com.docuhyphen.app.api.model.dto.AuditRetentionPolicyUpdateRequestDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{organizationId}/audit-retention-policies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditOrganizationRetentionPolicyResourceOperations
{
    @GET
    fun listOrganizationRetentionPolicies(@PathParam("organizationId") organizationId: String): Response

    @PUT
    @Path("/{category}")
    fun upsertOrganizationRetentionPolicy(
        @PathParam("organizationId") organizationId: String,
        @PathParam("category") category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response
}
