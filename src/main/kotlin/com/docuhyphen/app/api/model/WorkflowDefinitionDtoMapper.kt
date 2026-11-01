package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.workflow.SubjectFieldEntry
import com.docuhyphen.app.api.service.workflow.WorkflowSpecJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

object WorkflowDefinitionDtoMapper
{
    private val json = WorkflowSpecJson.instance

    fun toDto(definition: WorkflowDefinition): WorkflowDefinitionDto = WorkflowDefinitionDto(
        id = definition.id,
        name = definition.name,
        summary = definition.summary,
        description = definition.description,
        triggerEvent = definition.triggerEvent,
        version = definition.version,
        scope = definition.scope.name,
        isActive = definition.isActive,
        isPublished = definition.isPublished,
        isTemplate = definition.isTemplate,
        generalTags = decodeTags(definition.generalTags),
        organizationId = definition.organizationId,
        sourceTemplateId = definition.sourceTemplateId,
        createdByAppUserId = definition.createdByAppUserId,
        stepsJson = definition.stepsJson,
        createdAt = definition.createdAt,
    )

    fun toListItemDto(definition: WorkflowDefinition): WorkflowDefinitionListItemDto = WorkflowDefinitionListItemDto(
        id = definition.id,
        name = definition.name,
        summary = definition.summary,
        triggerEvent = definition.triggerEvent,
        version = definition.version,
        scope = definition.scope.name,
        isActive = definition.isActive,
        isPublished = definition.isPublished,
        isTemplate = definition.isTemplate,
        generalTags = decodeTags(definition.generalTags),
        organizationId = definition.organizationId,
        sourceTemplateId = definition.sourceTemplateId,
        createdAt = definition.createdAt,
    )

    fun toInstanceListItemDto(instance: WorkflowInstance, definitionName: String?): WorkflowInstanceListItemDto =
        WorkflowInstanceListItemDto(
            id = instance.id,
            definitionId = instance.definitionId,
            definitionName = definitionName,
            subjectResourceType = instance.subjectResourceType,
            subjectResourceId = instance.subjectResourceId,
            status = instance.status.name,
            currentStepIndex = instance.currentStepIndex,
            createdAt = instance.createdAt,
            completedAt = instance.completedAt,
        )

    fun toInstanceDetailDto(
        instance: WorkflowInstance,
        definitionName: String?,
        steps: List<WorkflowStepInstanceResponseDto>,
        transitions: List<WorkflowStepTransition>,
    ): WorkflowInstanceDetailResponseDto = WorkflowInstanceDetailResponseDto(
        id = instance.id,
        definitionId = instance.definitionId,
        definitionName = definitionName,
        subjectResourceType = instance.subjectResourceType,
        subjectResourceId = instance.subjectResourceId,
        status = instance.status.name,
        currentStepIndex = instance.currentStepIndex,
        steps = steps,
        definitionVersion = instance.definitionVersion,
        definitionSnapshotJson = instance.definitionSnapshotJson ?: "",
        transitions = transitions.map(::toTransitionDto),
        failureCode = instance.failureCode,
        createdAt = instance.createdAt,
        completedAt = instance.completedAt,
    )

    fun toTransitionDto(transition: WorkflowStepTransition): WorkflowStepTransitionResponseDto =
        WorkflowStepTransitionResponseDto(
            fromStepIndex = transition.fromStepIndex,
            toStepIndex = transition.toStepIndex,
            outcome = transition.outcome.name,
        )

    fun toStepDto(
        step: WorkflowStepInstance,
        assignees: List<WorkflowStepAssignee>,
        decisions: List<WorkflowStepDecision>,
        resolveDisplayInfo: (kind: String, principalId: String) -> Pair<String?, String?>,
    ): WorkflowStepInstanceResponseDto = WorkflowStepInstanceResponseDto(
        id = step.id,
        stepIndex = step.stepIndex,
        stepType = step.stepType.name,
        status = step.status.name,
        assignees = assignees.map { assignee ->
            val kind = assignee.principalKind.name
            val principalId = assignee.principalId.toString()
            val (displayName, email) = resolveDisplayInfo(kind, principalId)
            WorkflowPrincipalRefResponseDto(kind, principalId, displayName, email)
        },
        decisions = decisions.map { decision ->
            val kind = decision.principalKind.name
            val principalId = decision.principalId.toString()
            val (displayName, email) = resolveDisplayInfo(kind, principalId)
            WorkflowDecisionResponseDto(
                principalKind = kind,
                principalId = principalId,
                decision = decision.decision,
                reason = decision.reason,
                atEpochMillis = decision.decidedAt.time,
                displayName = displayName,
                email = email,
            )
        },
        dueAt = step.dueAt,
        escalatedAt = step.escalatedAt,
        completedAt = step.completedAt,
        createdAt = step.createdAt,
    )

    fun toTriggerDto(trigger: WorkflowTriggerEventRegistry): WorkflowTriggerEventResponseDto =
        WorkflowTriggerEventResponseDto(
            eventName = trigger.eventName,
            description = trigger.description,
            subjectFields = SubjectFieldEntry.parseList(trigger.subjectFieldsJson).map {
                WorkflowSubjectFieldResponseDto(it.name, it.type, it.description, it.lookupType)
            },
            isActive = trigger.isActive,
            subjectResourceType = trigger.subjectResourceType,
            subjectSchemaVersion = trigger.subjectSchemaVersion,
        )

    fun toClearanceStatusDto(
        ownInstances: List<WorkflowInstance>,
        counterpartyInstances: List<WorkflowInstance>,
    ): ExchangeClearanceStatusDto = ExchangeClearanceStatusDto(
        myOrg = toPartyClearanceDto(ownInstances),
        counterparties = counterpartyInstances
            .groupBy { it.organizationId }
            .values
            .map(::toPartyClearanceDto),
    )

    fun toPartyClearanceDto(instances: List<WorkflowInstance>): PartyClearanceDto
    {
        if (instances.isEmpty()) return PartyClearanceDto("NONE")
        val statuses = instances.map { it.status }
        return when
        {
            statuses.any { it.isActive } -> PartyClearanceDto("RUNNING")
            statuses.any { it == WorkflowInstanceStatus.REJECTED || it == WorkflowInstanceStatus.CANCELLED } ->
                PartyClearanceDto("BLOCKED")

            statuses.all { it == WorkflowInstanceStatus.COMPLETED } -> PartyClearanceDto("CLEARED")
            else -> PartyClearanceDto("NONE")
        }
    }

    fun toEntityRefDto(contact: UserContact): WorkflowEntityRefDto
    {
        val label = listOfNotNull(contact.contactFirstName, contact.contactLastName)
            .joinToString(" ").ifBlank { contact.contactEmail }
        return WorkflowEntityRefDto(
            id = contact.contactAppUserId.toString(),
            label = label,
            sublabel = contact.contactEmail,
        )
    }

    fun toEntityRefDto(group: PrincipalGroup): WorkflowEntityRefDto = WorkflowEntityRefDto(
        id = group.id.toString(),
        label = group.name,
        sublabel = group.description,
    )

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())
}
