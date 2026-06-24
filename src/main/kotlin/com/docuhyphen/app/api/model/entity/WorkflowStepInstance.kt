package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class WorkflowStepType
{
    APPROVAL,
    NOTIFICATION,
    CONDITION,
    ACTION,
    WAIT_FOR_COUNTERPARTY_CLEARANCE,
}

enum class WorkflowStepStatus
{
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED,
    SKIPPED,
    COMPLETED,
    /** Step is paused until all workflow instances on the counterparty side of the exchange reach a terminal state. */
    AWAITING_COUNTERPARTY,
}

/**
 * Concrete execution of one step inside a [WorkflowInstance].
 *
 * - `specSnapshotJson`      : the step's spec at instance start (replay-safe).
 * - `assigneesSnapshotJson` : resolved principals at instance start; subsequent group
 *                             membership changes don't move the targets mid-run.
 * - `decisionsJson`         : append-only list of `{principalKind, principalId, decision,
 *                             reason, at}` entries. Quorum is computed by counting.
 */
@Entity
@Serializable
@Table(name = "workflow_step_instance")
class WorkflowStepInstance
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "instance_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var instanceId: UUID

    @Column(name = "step_index", nullable = false)
    var stepIndex: Int = 0

    @Column(name = "step_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var stepType: WorkflowStepType = WorkflowStepType.APPROVAL

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var status: WorkflowStepStatus = WorkflowStepStatus.PENDING

    @Column(name = "spec_snapshot_json", nullable = false, columnDefinition = "text")
    lateinit var specSnapshotJson: String

    @Column(name = "assignees_snapshot_json", nullable = true, columnDefinition = "text")
    var assigneesSnapshotJson: String? = null

    @Column(name = "decisions_json", nullable = false, columnDefinition = "text")
    var decisionsJson: String = "[]"

    /**
     * Tracks per-addon fire state so the scheduler never double-sends a reminder.
     * JSON object keyed by addon index: `{ "0": { "fired": "true", "fireCount": "1",
     * "firedAt": "<epochMs>", "lastFiredAt": "<epochMs>" } }`.
     */
    @Column(name = "addons_state_json", nullable = false, columnDefinition = "text")
    var addonsStateJson: String = "{}"

    @Column(name = "due_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var dueAt: Timestamp? = null

    @Column(name = "escalated_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var escalatedAt: Timestamp? = null

    @Column(name = "completed_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var completedAt: Timestamp? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

