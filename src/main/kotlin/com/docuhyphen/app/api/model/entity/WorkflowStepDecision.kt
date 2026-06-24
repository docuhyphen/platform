package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * One decision (vote) recorded against a [WorkflowStepInstance]. Replaces the former
 * `decisions_json` read-modify-write blob. The unique constraint on
 * (step_instance_id, principal_kind, principal_id) enforces one vote per principal per
 * step at the database level; a re-vote updates the existing row.
 *
 * `decision` is a free string rather than an enum because the generic workflow engine
 * writes `APPROVE` / `REJECT` while the admin-approval flow writes `APPROVED`.
 */
@Entity
@Serializable
@Table(name = "workflow_step_decision")
class WorkflowStepDecision
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "step_instance_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var stepInstanceId: UUID

    @Column(name = "principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var principalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "principal_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var principalId: UUID

    @Column(name = "decision", nullable = false, length = 16)
    lateinit var decision: String

    @Column(name = "reason", nullable = true, columnDefinition = "text")
    var reason: String? = null

    @Column(name = "decided_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var decidedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
