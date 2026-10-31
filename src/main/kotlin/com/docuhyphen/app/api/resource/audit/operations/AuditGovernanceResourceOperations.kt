package com.docuhyphen.app.api.resource.audit.operations

import com.docuhyphen.app.api.model.dto.AuditEngagementCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditRetentionPolicyUpdateRequestDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AuditGovernanceResourceOperations {
    @GET
    @Path("/platform/audit-retention-policies")
    fun listPlatformRetentionPolicies(): Response

    @PUT
    @Path("/platform/audit-retention-policies/{category}")
    fun upsertPlatformRetentionPolicy(
        @PathParam("category") category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response

    @POST
    @Path("/platform/audit-legal-holds")
    fun placePlatformLegalHold(body: AuditLegalHoldCreateRequestDto): Response

    @GET
    @Path("/platform/audit-legal-holds")
    fun listPlatformLegalHolds(): Response

    @POST
    @Path("/platform/audit-legal-holds/{holdId}/release")
    fun releasePlatformLegalHold(@PathParam("holdId") holdId: String): Response

    @GET
    @Path("/platform/audit-analytics/reconciliation")
    fun getPlatformAnalyticsReconciliation(): Response

    @POST
    @Path("/platform/audit-engagements")
    fun requestPlatformEngagement(body: AuditEngagementCreateRequestDto): Response

    @GET
    @Path("/platform/audit-engagements")
    fun listPlatformEngagements(): Response

    @POST
    @Path("/platform/audit-engagements/{engagementId}/approve")
    fun approvePlatformEngagement(@PathParam("engagementId") engagementId: String): Response

    @POST
    @Path("/platform/audit-engagements/{engagementId}/revoke")
    fun revokePlatformEngagement(@PathParam("engagementId") engagementId: String): Response
}
