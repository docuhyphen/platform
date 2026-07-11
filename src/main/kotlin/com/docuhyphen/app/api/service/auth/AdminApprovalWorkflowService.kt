package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.model.entity.WorkflowStepDecision
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepDecisionRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Second-admin approval for sensitive admin actions, backed by the generic workflow engine
 * (replaces the bespoke `AdminApprovalRequest`). An approval is a single-step `APPROVAL`
 * [WorkflowInstance]: `initiate` opens it (RUNNING / step PENDING), `approve` closes it
 * (COMPLETED / step APPROVED) under a different admin, and `validateApprovedRequest` gates the
 * actual privileged operation. The "approvalId" exposed to the API is the [WorkflowInstance] id.
 */
@RequestScoped
class AdminApprovalWorkflowService @Inject constructor(
    private val workflowDefinitionRepository: WorkflowDefinitionRepository,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowStepInstanceRepository: WorkflowStepInstanceRepository,
    private val workflowStepDecisionRepository: WorkflowStepDecisionRepository,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private const val DEFINITION_NAME = "admin-action-approval"
        private const val DEFINITION_VERSION = 1
        private const val SUBJECT_TYPE = "ADMIN_ACTION"
        private val ACTION_REGEX = Regex(""""action"\s*:\s*"([^"]*)"""")
    }

    @Transactional
    fun initiate(action: String, requesterId: UUID, reason: String?, expiresMinutes: Long?, requestId: String?): WorkflowInstance
    {
        val expirationMinutes = expiresMinutes?.coerceIn(5, 240) ?: 60
        val now = Instant.now()
        val definition = ensureDefinition()

        val instance = workflowInstanceRepository.save(
            WorkflowInstance().apply {
                this.definitionId = definition.id
                this.definitionVersion = definition.version
                this.subjectResourceType = SUBJECT_TYPE
                this.status = WorkflowInstanceStatus.RUNNING
                this.currentStepIndex = 0
                this.initiatedByAppUserId = requesterId
                this.subjectDataJson = buildSubjectJson(action, reason)
                // This subsystem closes the instance here and never routes it through the shared
                // engine, but the frozen execution fields are still populated so no instance row
                // is left without a snapshot or trigger to satisfy the shared instance contract.
                this.definitionSnapshotJson = definition.stepsJson
                this.triggerEventSnapshot = definition.triggerEvent
                this.createdAt = Timestamp.from(now)
            }
        )

        workflowStepInstanceRepository.save(
            WorkflowStepInstance().apply {
                this.instanceId = instance.id
                this.stepIndex = 0
                this.stepType = WorkflowStepType.APPROVAL
                this.status = WorkflowStepStatus.PENDING
                this.specSnapshotJson = """{"type":"APPROVAL","quorum":{"kind":"ANY"}}"""
                this.dueAt = Timestamp.from(now.plusSeconds(expirationMinutes * 60))
            }
        )

        authAuditService.emit(
            action = "ADMIN_APPROVAL_INITIATE",
            outcome = "SUCCESS",
            actorId = requesterId,
            requestId = requestId,
        )

        return instance
    }

    @Transactional
    fun approve(approvalId: UUID, approverId: UUID, requestId: String?): WorkflowInstance
    {
        val instance = workflowInstanceRepository.findById(approvalId)
            ?: throw IllegalArgumentException("Approval request not found")

        // Admin-action approvals are a self-contained single-step subsystem: they are opened and
        // closed here and never routed through the shared engine's SLA escalation, so a pending
        // one is only ever RUNNING. ESCALATED is deliberately not accepted as pending here.
        if (instance.status != WorkflowInstanceStatus.RUNNING)
        {
            throw IllegalArgumentException("Approval request is not pending")
        }
        if (instance.initiatedByAppUserId == approverId)
        {
            throw IllegalArgumentException("Requester cannot approve their own request")
        }

        val step = workflowStepInstanceRepository.findCurrent(instance.id, instance.currentStepIndex)
            ?: throw IllegalArgumentException("Approval step not found")

        val now = Timestamp.from(Instant.now())
        if (step.dueAt?.before(now) == true)
        {
            throw IllegalArgumentException("Approval request expired")
        }

        step.status = WorkflowStepStatus.APPROVED
        step.completedAt = now
        workflowStepInstanceRepository.update(step)
        workflowStepDecisionRepository.save(
            WorkflowStepDecision().apply {
                this.stepInstanceId = step.id
                this.principalKind = PrincipalKind.USER
                this.principalId = approverId
                this.decision = "APPROVED"
                this.decidedAt = now
            }
        )

        instance.status = WorkflowInstanceStatus.COMPLETED
        instance.completedAt = now
        val updated = workflowInstanceRepository.update(instance)

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
        val instance = workflowInstanceRepository.findById(approvalId)
            ?: throw IllegalArgumentException("Approval request not found")

        if (instance.status != WorkflowInstanceStatus.COMPLETED)
        {
            throw IllegalArgumentException("Approval request is not approved")
        }
        if (parseAction(instance.subjectDataJson) != action)
        {
            throw IllegalArgumentException("Approval request action mismatch")
        }
        if (instance.initiatedByAppUserId != actorId)
        {
            throw IllegalArgumentException("Approval request actor mismatch")
        }

        val step = workflowStepInstanceRepository.findCurrent(instance.id, 0)
        if (step?.dueAt?.before(Timestamp.from(Instant.now())) == true)
        {
            throw IllegalArgumentException("Approval request expired")
        }
    }

    private fun ensureDefinition(): WorkflowDefinition =
        workflowDefinitionRepository.findByNameAndVersion(DEFINITION_NAME, DEFINITION_VERSION)
            ?: workflowDefinitionRepository.save(
                WorkflowDefinition().apply {
                    this.name = DEFINITION_NAME
                    this.version = DEFINITION_VERSION
                    this.scope = WorkflowScope.APP
                    this.triggerEvent = "admin.action.approval_requested"
                    this.stepsJson = """{"steps":[{"type":"APPROVAL","quorum":{"kind":"ANY"}}]}"""
                    this.description = "Second-admin approval for sensitive admin actions"
                }
            )

    private fun buildSubjectJson(action: String, reason: String?): String
    {
        val reasonPart = reason?.trim()?.take(1024)?.let { ""","reason":"${jsonEscape(it)}"""" } ?: ""
        return """{"action":"${jsonEscape(action)}"$reasonPart}"""
    }

    private fun parseAction(subjectDataJson: String?): String? =
        subjectDataJson?.let { ACTION_REGEX.find(it)?.groupValues?.get(1) }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
