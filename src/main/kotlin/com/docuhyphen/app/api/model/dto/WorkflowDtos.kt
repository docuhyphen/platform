package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class WorkflowDefinitionDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val summary: String?,
    val description: String?,
    val triggerEvent: String,
    val version: Int,
    val scope: String,
    val isActive: Boolean,
    val isPublished: Boolean,
    val isTemplate: Boolean,
    val generalTags: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val sourceTemplateId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val createdByAppUserId: UUID?,
    val stepsJson: String,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
)

@Serializable
data class WorkflowDefinitionListItemDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val summary: String?,
    val triggerEvent: String,
    val version: Int,
    val scope: String,
    val isActive: Boolean,
    val isPublished: Boolean,
    val isTemplate: Boolean,
    val generalTags: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val sourceTemplateId: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
)

@Serializable
data class WorkflowTriggerEventResponseDto(
    val eventName: String,
    val description: String?,
    val subjectFields: List<WorkflowSubjectFieldResponseDto>,
    val isActive: Boolean,
    val subjectResourceType: String,
    val subjectSchemaVersion: Int,
)

@Serializable
data class WorkflowSubjectFieldResponseDto(
    val name: String,
    val type: String,
    val description: String?,
    val lookupType: String? = null,
)

@Serializable
data class WorkflowEntityRefDto(
    val id: String,
    val label: String,
    val sublabel: String? = null,
)

@Serializable
data class WorkflowInstanceListItemDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val definitionId: UUID,
    val definitionName: String?,
    val subjectResourceType: String?,
    @Serializable(with = UUIDSerializer::class)
    val subjectResourceId: UUID?,
    val status: String,
    val currentStepIndex: Int,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val completedAt: Timestamp?,
)

@Serializable
data class WorkflowInstanceDetailResponseDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val definitionId: UUID,
    val definitionName: String?,
    val subjectResourceType: String?,
    @Serializable(with = UUIDSerializer::class)
    val subjectResourceId: UUID?,
    val status: String,
    val currentStepIndex: Int,
    val steps: List<WorkflowStepInstanceResponseDto>,
    val definitionVersion: Int,
    val definitionSnapshotJson: String,
    val transitions: List<WorkflowStepTransitionResponseDto>,
    val failureCode: String? = null,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val completedAt: Timestamp?,
)

@Serializable
data class WorkflowStepTransitionResponseDto(
    val fromStepIndex: Int?,
    val toStepIndex: Int?,
    val outcome: String,
)

@Serializable
data class WorkflowStepInstanceResponseDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val stepIndex: Int,
    val stepType: String,
    val status: String,
    val assignees: List<WorkflowPrincipalRefResponseDto>,
    val decisions: List<WorkflowDecisionResponseDto>,
    @Serializable(with = TimestampSerializer::class)
    val dueAt: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val escalatedAt: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val completedAt: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
)

@Serializable
data class WorkflowPrincipalRefResponseDto(
    val kind: String,
    val id: String,
    val displayName: String? = null,
    val email: String? = null,
)

@Serializable
data class WorkflowDecisionResponseDto(
    val principalKind: String,
    val principalId: String,
    val decision: String,
    val reason: String?,
    val atEpochMillis: Long,
    val displayName: String? = null,
    val email: String? = null,
)

@Serializable
data class PartyClearanceDto(
    val status: String,
)

@Serializable
data class ExchangeClearanceStatusDto(
    val myOrg: PartyClearanceDto,
    val counterparties: List<PartyClearanceDto>,
)

