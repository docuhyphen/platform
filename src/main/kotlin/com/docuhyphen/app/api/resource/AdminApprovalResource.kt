package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.resource.model.AdminApprovalApproveResponse
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateRequest
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalWorkflowService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/auth/admin-approvals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AdminApprovalResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val adminApprovalWorkflowService: AdminApprovalWorkflowService,
)
{
    @POST
    @Path("/initiate")
    fun initiate(
        payload: AdminApprovalInitiateRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val actor = authTokenContext.authToken.appUser
                ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

            if (actor.role != AppUserRole.ORG_ADMIN)
            {
                return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
            }

            if (payload.action.isBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST).entity(ResponseError("Action is required")).build()
            }

            val approval = adminApprovalWorkflowService.initiate(
                action = payload.action.trim(),
                requesterId = actor.id,
                reason = payload.reason,
                expiresMinutes = payload.expiresMinutes,
                requestId = requestId,
            )

            Response.ok(
                AdminApprovalInitiateResponse(
                    approvalId = approval.id.toString(),
                    status = approval.status.name,
                )
            ).build()
        }
        catch (e: Exception)
        {
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
    }

    @POST
    @Path("/{approvalId}/approve")
    fun approve(
        @PathParam("approvalId") approvalId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val actor = authTokenContext.authToken.appUser
                ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

            if (actor.role != AppUserRole.ORG_ADMIN)
            {
                return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
            }

            val approved = adminApprovalWorkflowService.approve(
                approvalId = UUID.fromString(approvalId),
                approverId = actor.id,
                requestId = requestId,
            )

            Response.ok(
                AdminApprovalApproveResponse(
                    approvalId = approved.id.toString(),
                    status = approved.status.name,
                )
            ).build()
        }
        catch (e: Exception)
        {
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
    }
}

