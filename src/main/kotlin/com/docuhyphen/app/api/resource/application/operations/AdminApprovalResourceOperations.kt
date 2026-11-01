package com.docuhyphen.app.api.resource.application.operations

import com.docuhyphen.app.api.resource.RequestHeaders
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/admin-approvals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AdminApprovalResourceOperations
{
    @POST
    @Path("/initiate")
    fun initiate(
        payload: AdminApprovalInitiateRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @POST
    @Path("/{approvalId}/approve")
    fun approve(
        @PathParam("approvalId") approvalId: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
