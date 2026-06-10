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

