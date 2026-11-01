package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class WorkflowTransitionOutcome
{
    DEFAULT,
    APPROVE,
    REJECT,
    TRUE,
    FALSE,
    FAILED,
}

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
