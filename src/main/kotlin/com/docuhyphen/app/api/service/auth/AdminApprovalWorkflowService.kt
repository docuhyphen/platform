package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AdminApprovalRequest
import com.docuhyphen.app.api.model.entity.AdminApprovalStatus
import com.docuhyphen.app.api.repository.AdminApprovalRequestRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class AdminApprovalWorkflowService @Inject constructor(
    private val adminApprovalRequestRepository: AdminApprovalRequestRepository,
    private val authAuditService: AuthAuditService,
)
{
    @Transactional
    fun initiate(action: String, requesterId: UUID, reason: String?, expiresMinutes: Long?, requestId: String?): AdminApprovalRequest
    {
        val expirationMinutes = expiresMinutes?.coerceIn(5, 240) ?: 60
        val approval = adminApprovalRequestRepository.save(
            AdminApprovalRequest().apply {
                this.action = action
                this.requesterId = requesterId
                this.reason = reason?.trim()?.take(1024)
                this.createdDate = Timestamp.from(Instant.now())
                this.expiresAt = Timestamp.from(Instant.now().plusSeconds(expirationMinutes * 60))
                this.status = AdminApprovalStatus.PENDING
            }
        )

        authAuditService.emit(
            action = "ADMIN_APPROVAL_INITIATE",
            outcome = "SUCCESS",
            actorId = requesterId,
            requestId = requestId,
        )

        return approval
    }

    @Transactional
    fun approve(approvalId: UUID, approverId: UUID, requestId: String?): AdminApprovalRequest
    {
        adminApprovalRequestRepository.expirePendingApprovals(Instant.now())
        val approval = adminApprovalRequestRepository.findByApprovalId(approvalId)
            ?: throw IllegalArgumentException("Approval request not found")

        if (approval.status != AdminApprovalStatus.PENDING)
        {
            throw IllegalArgumentException("Approval request is not pending")
        }

        if (approval.requesterId == approverId)
        {
            throw IllegalArgumentException("Requester cannot approve their own request")
        }

        approval.status = AdminApprovalStatus.APPROVED
        approval.approverId = approverId
        approval.approvedDate = Timestamp.from(Instant.now())
        val updated = adminApprovalRequestRepository.update(approval)

        authAuditService.emit(
            action = "ADMIN_APPROVAL_APPROVE",
            outcome = "SUCCESS",
            actorId = approverId,
            requestId = requestId,
        )

        return updated
    }

    fun validateApprovedRequest(action: String, approvalId: UUID, actorId: UUID)
    {
        val approval = adminApprovalRequestRepository.findByApprovalId(approvalId)
            ?: throw IllegalArgumentException("Approval request not found")

        if (approval.status != AdminApprovalStatus.APPROVED)
        {
            throw IllegalArgumentException("Approval request is not approved")
        }

        if (approval.action != action)
        {
            throw IllegalArgumentException("Approval request action mismatch")
        }

        if (approval.requesterId != actorId)
        {
            throw IllegalArgumentException("Approval request actor mismatch")
        }

        if (approval.expiresAt.before(Timestamp.from(Instant.now())))
        {
            throw IllegalArgumentException("Approval request expired")
        }
    }
}

