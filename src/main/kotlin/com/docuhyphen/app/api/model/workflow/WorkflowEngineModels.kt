package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import kotlinx.serialization.Serializable
import java.util.*

data class TriggerRequest(
    val triggerEvent: String,
    val subjectResourceType: String? = null,
    val subjectResourceId: UUID? = null,
    val organizationId: UUID? = null,
    val subjectData: Map<String, String> = emptyMap(),
    val initiatedByAppUserId: UUID? = null,
    val ownerAppUserId: UUID? = null,
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

@Serializable
data class PendingWorkflowStepDto(
    val stepInstanceId: String,
    val workflowInstanceId: String,
    val stepType: String,
    val exchangeId: String? = null,
    val name: String? = null,
    val requestedByEmail: String? = null,
    val requestedByName: String? = null,
    val groupName: String? = null,
    val createdAtEpochMillis: Long,
)
