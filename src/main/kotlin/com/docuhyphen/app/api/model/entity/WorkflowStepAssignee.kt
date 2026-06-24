package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.util.*

/**
 * One resolved principal assigned to a [WorkflowStepInstance] at the moment the step
 * was materialised. Replaces the former `assignees_snapshot_json` blob so assignees can
 * be looked up by principal with an indexed query (inbox, cross-org approver checks)
 * instead of a substring LIKE on JSON.
 *
 * On SLA escalation the rows for a step are replaced with the escalation targets.
 */
@Entity
@Serializable
@Table(name = "workflow_step_assignee")
class WorkflowStepAssignee
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

    constructor()
}
