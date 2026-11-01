package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.workflow.*
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

interface WorkflowEngineService
{
    fun trigger(request: TriggerRequest): TriggerResult?

    fun recordDecision(
        stepInstanceId: UUID,
        decider: PrincipalRef,
        decision: Decision,
        reason: String? = null,
    ): DecisionResult

    fun escalateOverdue(now: java.sql.Timestamp): Int

    fun cancel(instanceId: UUID, reason: String?)

    fun cancelActiveForSubject(subjectResourceType: String, subjectResourceId: UUID, reason: String?): Int

    fun listPendingForUser(appUserId: UUID): List<PendingWorkflowStepDto>

    fun decisionsOf(instanceId: UUID): List<WorkflowDecisionRecord>
}

enum class Decision
{
    APPROVE,
    REJECT,
}
