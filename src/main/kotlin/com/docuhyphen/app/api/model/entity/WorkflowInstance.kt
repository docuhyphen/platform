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

    FAILED,
    ;

    val isActive: Boolean
        get() = this == RUNNING || this == ESCALATED

    companion object
    {
        val ACTIVE: Set<WorkflowInstanceStatus> = setOf(RUNNING, ESCALATED)
    }
}

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

    @Column(name = "definition_snapshot_json", nullable = true, columnDefinition = "text")
    var definitionSnapshotJson: String? = null

    @Column(name = "trigger_event_snapshot", nullable = true, length = 128)
    var triggerEventSnapshot: String? = null

    @Column(name = "failure_code", nullable = true, length = 64)
    var failureCode: String? = null

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

