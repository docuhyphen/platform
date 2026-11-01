package com.docuhyphen.app.api.resource.audit.operations

import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/organizations/{organizationId}/audit-exports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditOrganizationExportsResourceOperations
{
    @POST
    fun requestOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        body: AuditExportCreateRequestDto,
    ): Response

    @GET
    fun listOrganizationExports(@PathParam("organizationId") organizationId: String): Response

    @GET
    @Path("/{exportId}")
    fun getOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
    ): Response

    @POST
    @Path("/{exportId}/approvals")
    fun approveOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
        body: AuditExportApprovalRequestDto?,
    ): Response

    @GET
    @Path("/{exportId}/approvals")
    fun listOrganizationExportApprovals(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
    ): Response

    @GET
    @Path("/{exportId}/file")
    @Produces("application/zip")
    fun downloadOrganizationExport(
        @PathParam("organizationId") organizationId: String,
        @PathParam("exportId") exportId: String,
    ): Response
}
