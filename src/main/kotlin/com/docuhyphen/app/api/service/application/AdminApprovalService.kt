package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.AdminApprovalDtoMapper
import com.docuhyphen.app.api.resource.model.AdminApprovalApproveResponse
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateRequest
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateResponse
import com.docuhyphen.app.api.service.auth.AdminApprovalWorkflowService
import com.docuhyphen.app.api.service.auth.UserRoleService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AdminApprovalService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val adminApprovalWorkflowService: AdminApprovalWorkflowService,
)
{
    fun initiate(request: AdminApprovalInitiateRequest, requestId: String?): AdminApprovalInitiateResponse
    {
        val actorId = requireOrgAdmin()
        if (request.action.isBlank())
        {
            throw IllegalArgumentException("Action is required")
        }
        val approval = adminApprovalWorkflowService.initiate(
            action = request.action.trim(),
            requesterId = actorId,
            reason = request.reason,
            expiresMinutes = request.expiresMinutes,
            requestId = requestId,
        )
        return AdminApprovalDtoMapper.toInitiateResponse(approval)
    }

    fun approve(approvalId: UUID, requestId: String?): AdminApprovalApproveResponse
    {
        val actorId = requireOrgAdmin()
        val approved = adminApprovalWorkflowService.approve(
            approvalId = approvalId,
            approverId = actorId,
            requestId = requestId,
        )
        return AdminApprovalDtoMapper.toApproveResponse(approved)
    }

    private fun requireOrgAdmin(): UUID
    {
        val actor = authTokenContext.authToken.appUser ?: throw UnauthorizedException("Unauthorized")
        if (!userRoleService.isOrgAdmin(actor.id))
        {
            throw ForbiddenException("Insufficient privileges")
        }
        return actor.id
    }
}
