package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.util.UUID

/**
 * Generic workflow engine. The first registered workflow (`session-approval-in-group`,
 * seeded by V9) gates [com.docuhyphen.app.api.model.entity.SharingSession] activation
 * inside groups that require approval; new workflows simply need a [WorkflowSpec]
 * persisted as a [com.docuhyphen.app.api.model.entity.WorkflowDefinition].
 *
 * Implementation in [DefaultWorkflowEngineService].
 *
 * Lifecycle:
 *   1. `trigger(...)`  — invoked by a domain service (e.g. SharingSessionInitiationService
 *      in iteration 4) when an event fires. The engine resolves the matching definition
 *      via [com.docuhyphen.app.api.repository.WorkflowDefinitionRepository.findActiveForTrigger]
 *      and creates a [com.docuhyphen.app.api.model.entity.WorkflowInstance] + first
 *      [com.docuhyphen.app.api.model.entity.WorkflowStepInstance] with resolved assignees.
 *   2. `recordDecision(...)` — invoked by an assignee approving/rejecting. The engine
 *      appends to `decisions_json`, checks quorum, and either advances to the next
 *      step, completes/rejects the instance, or stays pending.
 *   3. SLA breach handling — iteration 3 will add a scheduled job that calls
 *      `escalateOverdue(...)` periodically.
 */
interface WorkflowEngineService
{
    /**
     * Fire an event. If a matching active [WorkflowDefinition] exists for the current
     * scope (org-specific first, app-wide fallback), creates and returns a new
     * instance. If no matching definition exists, returns null (caller proceeds as if
     * no workflow was required).
     */
    fun trigger(request: TriggerRequest): TriggerResult?

    /**
     * Record an approve/reject decision against a pending step instance. Returns the
     * resulting workflow state (one of WorkflowInstanceStatus + the index of the now-current step).
     */
    fun recordDecision(
        stepInstanceId: UUID,
        decider: PrincipalRef,
        decision: Decision,
        reason: String? = null,
    ): DecisionResult

    /**
     * Iteration-3 hook: scan PENDING steps whose `due_at` has elapsed and apply
     * [com.docuhyphen.app.api.service.workflow.EscalationAction]. No-op until the
     * scheduler bean is wired.
     */
    fun escalateOverdue(now: java.sql.Timestamp): Int

    /** Cancels a running workflow (e.g. when the subject session is deleted). */
    fun cancel(instanceId: UUID, reason: String?)

    /**
     * Pending APPROVAL step instances where [appUserId] is either a direct
     * assignee (USER kind) or a member of an assignee group (PRINCIPAL_GROUP kind). Powers
     * the "Pending approvals" inbox so a page refresh doesn't drop missed realtime pushes.
     */
    fun listPendingForUser(appUserId: UUID): List<PendingWorkflowStepDto>
}

enum class Decision
{
    APPROVE,
    REJECT,
}

/**
 * @param triggerEvent       e.g. "session.approval_requested"
 * @param subjectResourceType e.g. "SHARING_SESSION"
 * @param subjectResourceId  the subject's UUID
 * @param organizationId     org context — drives org-scope definition lookup
 * @param subjectData        frozen fields the workflow may reference via `$subject.<key>`
 * @param initiatedByAppUserId  who initiated the trigger; surfaces in audit
 */
data class TriggerRequest(
    val triggerEvent: String,
    val subjectResourceType: String? = null,
    val subjectResourceId: UUID? = null,
    val organizationId: UUID? = null,
    val subjectData: Map<String, String> = emptyMap(),
    val initiatedByAppUserId: UUID? = null,
)

data class TriggerResult(
    val instanceId: UUID,
    val definitionId: UUID,
    val firstStepInstanceId: UUID,
    val firstStepAssignees: List<PrincipalRef>,
    val emittedEvents: List<String> = emptyList(),
)

data class DecisionResult(
    val instanceId: UUID,
    val stepInstanceId: UUID,
    val stepStatus: com.docuhyphen.app.api.model.entity.WorkflowStepStatus,
    val instanceStatus: com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus,
    val emittedEvents: List<String> = emptyList(),
)

/**
 * Shape returned by [WorkflowEngineService.listPendingForUser]. Mirrors the
 * frontend `PendingWorkflowStep` interface so the inbox can render without an extra mapping
 * layer. Times are epoch millis (ISO-8601 conversion happens client-side).
 */
@kotlinx.serialization.Serializable
data class PendingWorkflowStepDto(
    val stepInstanceId: String,
    val workflowInstanceId: String,
    val stepType: String,
    val sessionId: String? = null,
    val sessionName: String? = null,
    val requestedByEmail: String? = null,
    val requestedByName: String? = null,
    val groupName: String? = null,
    val createdAtEpochMillis: Long,
)

