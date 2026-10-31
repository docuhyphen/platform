package com.docuhyphen.app.api.resource.audit.operations

import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditExportResourceOperations {
    @POST
    @Path("/platform/audit-exports")
    fun requestPlatformExport(body: AuditExportCreateRequestDto): Response

    @GET
    @Path("/platform/audit-exports")
    fun listPlatformExports(): Response

    @GET
    @Path("/platform/audit-exports/{exportId}")
    fun getPlatformExport(@PathParam("exportId") exportId: String): Response

    @POST
    @Path("/platform/audit-exports/{exportId}/approvals")
    fun approvePlatformExport(@PathParam("exportId") exportId: String, body: AuditExportApprovalRequestDto?): Response

    @GET
    @Path("/platform/audit-exports/{exportId}/approvals")
    fun listPlatformExportApprovals(@PathParam("exportId") exportId: String): Response

    @GET
    @Path("/platform/audit-exports/{exportId}/file")
    @Produces("application/zip")
    fun downloadPlatformExport(@PathParam("exportId") exportId: String): Response

    @GET
    @Path("/platform/audit-integrity")
    fun getPlatformIntegrity(): Response
}
