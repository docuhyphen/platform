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
    val lookupType: String? = null,
)

/** Returned by GET /workflows/entity-lookup — a resolved entity ref for the entity picker. */
@Serializable
data class WorkflowEntityRefDto(
    val id: String,
    val label: String,
    val sublabel: String? = null,
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
    /** Definition version frozen at instance start; never changes on later definition edits. */
    val definitionVersion: Int,
    /**
     * Raw definition steps_json frozen at instance start. Reproduces the exact builder topology
     * and labels (including step names) so the Exchange diagram matches the definition preview.
     */
    val definitionSnapshotJson: String,
    /** Explicitly traversed edges, oldest first. The only source of edge traversal for the diagram. */
    val transitions: List<WorkflowStepTransitionResponseDto>,
    /**
     * Safe machine failure code when [status] is FAILED (e.g. `SNAPSHOT_MISSING`,
     * `SNAPSHOT_CORRUPT`); null otherwise. Never carries raw JSON or stack detail.
     */
    val failureCode: String? = null,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val completedAt: Timestamp?,
)

/**
 * One traversed edge in an instance's execution graph. `fromStepIndex` is null for the START
 * edge (entry into the first step); `toStepIndex` is null for a terminal edge (the instance ends).
 * `outcome` is one of DEFAULT, APPROVE, REJECT, TRUE, FALSE and identifies which configured branch
 * was taken, matching the definition-graph edge outcomes.
 */
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

// ── Exchange clearance status DTOs ────────────────────────────────────────────

/** Aggregate clearance status for one party (my org or a counterparty). */
@Serializable
data class PartyClearanceDto(
    /** NONE | RUNNING | CLEARED | BLOCKED */
    val status: String,
)

/**
 * Response for GET /exchanges/{id}/workflow-clearance-status.
 * Exposes aggregate workflow progress per party without leaking internal step details.
 */
@Serializable
data class ExchangeClearanceStatusDto(
    val myOrg: PartyClearanceDto,
    /** One entry per counterparty org — no org name or internal detail. */
    val counterparties: List<PartyClearanceDto>,
)

