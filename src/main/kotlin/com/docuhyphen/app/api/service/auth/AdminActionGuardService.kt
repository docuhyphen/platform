package com.docuhyphen.app.api.service.auth

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

data class AdminApprovalContext(
    val stepUpAuthenticated: Boolean,
    val dualApprovalId: String? = null,
    val requestId: String? = null,
)

@RequestScoped
class AdminActionGuardService @Inject constructor(
    private val authAuditService: AuthAuditService,
    private val adminApprovalWorkflowService: AdminApprovalWorkflowService,
)
{
    fun enforce(
        action: String,
        actorId: UUID?,
        context: AdminApprovalContext,
        requireDualApproval: Boolean = false,
    )
    {
        if (!context.stepUpAuthenticated)
        {
            authAuditService.emit(
                action = action,
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                actorId = actorId,
                requestId = context.requestId,
            )
            throw IllegalArgumentException("Step-up authentication is required for this action")
        }

        if (requireDualApproval && context.dualApprovalId.isNullOrBlank())
        {
            authAuditService.emit(
                action = action,
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                actorId = actorId,
                requestId = context.requestId,
            )
            throw IllegalArgumentException("Dual approval is required for this action")
        }

        if (requireDualApproval)
        {
            val actor = actorId ?: throw IllegalArgumentException("Actor is required for dual approval validation")
            val approvalId = runCatching { java.util.UUID.fromString(context.dualApprovalId) }.getOrNull()
                ?: throw IllegalArgumentException("Dual approval ID is invalid")

            adminApprovalWorkflowService.validateApprovedRequest(
                action = action,
                approvalId = approvalId,
                actorId = actor,
            )
        }

        authAuditService.emit(
            action = action,
            outcome = "SUCCESS",
            actorId = actorId,
            requestId = context.requestId,
        )
    }
}


