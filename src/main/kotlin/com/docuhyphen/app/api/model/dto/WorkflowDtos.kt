package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

// ── Workflow Definition DTOs ──────────────────────────────────────────────────

/** Full workflow definition, including the raw stepsJson DSL blob. Returned by GET /workflows/definitions/{id}. */
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
    val isTemplate: Boolean,
    val industryTags: List<String>,
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

/** Summary view without stepsJson. Used for list responses (GET /workflows/definitions). */
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
    val isTemplate: Boolean,
    val industryTags: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val sourceTemplateId: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
)

// ── Trigger Event DTOs ────────────────────────────────────────────────────────

@Serializable
data class WorkflowTriggerEventResponseDto(
    val eventName: String,
    val description: String?,
    val subjectFields: List<WorkflowSubjectFieldResponseDto>,
    val isActive: Boolean,
)

@Serializable
data class WorkflowSubjectFieldResponseDto(
    val name: String,
    val type: String,
    val description: String?,
)

// ── Instance DTOs ─────────────────────────────────────────────────────────────

/** Paginated list item for GET /workflows/instances. */
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

/** Full instance detail including step timeline. Returned by GET /workflows/instances/{id}. */
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
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val completedAt: Timestamp?,
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
data class WorkflowPrincipalRefResponseDto(val kind: String, val id: String)

@Serializable
data class WorkflowDecisionResponseDto(
    val principalKind: String,
    val principalId: String,
    val decision: String,
    val reason: String?,
    val atEpochMillis: Long,
)

