package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * The outcome that identifies which configured branch a [WorkflowStepInstance]
 * advanced through. Values mirror the frontend definition-graph edge outcomes so a
 * recorded transition lines up with exactly one edge in the frozen definition graph:
 *
 * - `DEFAULT`  : neutral continuation (START entry edge, WAIT_FOR_COUNTERPARTY_CLEARANCE
 *                clearance, and unknown/action fallbacks that follow `onApprove`).
 * - `APPROVE`  : approval quorum met, notification/action completion (`onApprove`).
 * - `REJECT`   : rejection or action failure (`onReject`, terminal).
 * - `TRUE`     : condition predicate true (`onTrue`).
 * - `FALSE`    : condition predicate false (`onFalse`).
 */
enum class WorkflowTransitionOutcome
{
    DEFAULT,
    APPROVE,
    REJECT,
    TRUE,
    FALSE,
}

/**
 * One traversed edge in a [WorkflowInstance]'s execution graph, recorded at the moment
 * the engine advances or terminates. This is the authoritative, explicit source of
 * traversed edges for the Exchange workflow diagram: the frontend never infers traversal
 * from step order, status, or timestamps.
 *
 * - `fromStepInstanceId` / `fromStepIndex` are null for the START edge (entry into the
 *   first step).
 * - `toStepIndex` is null for a terminal edge (the instance ends: END target, rejection,
 *   action failure, or an out-of-range branch target that completes the instance).
 *
 * A partial unique index on `fromStepInstanceId` guarantees one transition per source
 * step instance, so repeated scheduler ticks or retries never duplicate an edge; a second
 * partial unique index guarantees a single START edge per instance.
 */
@Entity
@Serializable
@Table(name = "workflow_step_transition")
class WorkflowStepTransition
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "instance_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var instanceId: UUID

    @Column(name = "from_step_instance_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var fromStepInstanceId: UUID? = null

    @Column(name = "from_step_index", nullable = true)
    var fromStepIndex: Int? = null

    @Column(name = "to_step_index", nullable = true)
    var toStepIndex: Int? = null

    @Column(name = "outcome", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var outcome: WorkflowTransitionOutcome = WorkflowTransitionOutcome.DEFAULT

    @Column(name = "recorded_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
