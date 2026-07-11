package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class WorkflowInstanceStatus
{
    RUNNING,
    COMPLETED,
    REJECTED,
    CANCELLED,
    ESCALATED,

    /**
     * Terminal status for an instance the engine could not execute safely: a missing or
     * corrupt execution snapshot, or an impossible transition. A failed instance never fires
     * a lifecycle terminal event, so the subject Exchange is left untouched for an operator to
     * inspect and recover manually.
     */
    FAILED,
    ;

    /**
     * Whether this instance is still in flight and should gate user actions. An SLA breach can
     * move a pending instance to [ESCALATED] without completing it, so both [RUNNING] and
     * [ESCALATED] are active. Terminal statuses ([COMPLETED], [REJECTED], [CANCELLED], [FAILED])
     * are not.
     */
    val isActive: Boolean
        get() = this == RUNNING || this == ESCALATED

    companion object
    {
        /** The set of statuses for which [isActive] is true. Use this for queries filtering on active instances. */
        val ACTIVE: Set<WorkflowInstanceStatus> = setOf(RUNNING, ESCALATED)
    }
}

/**
 * A running execution of a [WorkflowDefinition]. Always captures the exact
 * `definitionVersion` it started under so a later version bump never mutates
 * in-flight state.
 *
 * `subjectDataJson` is the engine's frozen view of fields it needs to resolve
 * placeholders (`$subject.recipientGroupId`, `$subject.orgId`, …), keeping it on
 * the row means the engine never has to dereference the subject mid-run.
 */
@Entity
@Serializable
@Table(name = "workflow_instance")
class WorkflowInstance
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "definition_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var definitionId: UUID

    @Column(name = "definition_version", nullable = false)
    var definitionVersion: Int = 1

    @Column(name = "subject_resource_type", nullable = true, length = 32)
    var subjectResourceType: String? = null

    @Column(name = "subject_resource_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var subjectResourceId: UUID? = null

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var status: WorkflowInstanceStatus = WorkflowInstanceStatus.RUNNING

    @Column(name = "current_step_index", nullable = false)
    var currentStepIndex: Int = 0

    @Column(name = "subject_data_json", nullable = true, columnDefinition = "text")
    var subjectDataJson: String? = null

    /**
     * The exact raw `workflow_definition.steps_json` frozen when this instance started.
     * Lets the Exchange diagram reproduce the same topology and labels the builder showed
     * (including the frontend-only step `name` that per-step snapshots drop) without ever
     * reading the later, mutable definition. Always set by the engine at instance start.
     */
    @Column(name = "definition_snapshot_json", nullable = true, columnDefinition = "text")
    var definitionSnapshotJson: String? = null

    /**
     * The trigger event name frozen when this instance started. Terminal fallback events and the
     * condition-step subject-field registry are derived from this frozen value, never from the
     * later, mutable definition, so a definition retargeted to a different trigger cannot change
     * how an in-flight instance ends or evaluates conditions. Always set by the engine at start.
     */
    @Column(name = "trigger_event_snapshot", nullable = true, length = 128)
    var triggerEventSnapshot: String? = null

    /**
     * Short, safe machine code describing why the instance reached [WorkflowInstanceStatus.FAILED]
     * (e.g. `SNAPSHOT_MISSING`, `SNAPSHOT_CORRUPT`). Null unless the instance failed. Safe to
     * surface to administrators; carries no raw JSON or stack detail.
     */
    @Column(name = "failure_code", nullable = true, length = 64)
    var failureCode: String? = null

    /**
     * Internal, operator-only detail for a failed instance. Never exposed through an API response;
     * present only for server-side diagnosis alongside the operational error log.
     */
    @Column(name = "failure_detail", nullable = true, columnDefinition = "text")
    var failureDetail: String? = null

    @Column(name = "initiated_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var initiatedByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "completed_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var completedAt: Timestamp? = null

    constructor()
}

