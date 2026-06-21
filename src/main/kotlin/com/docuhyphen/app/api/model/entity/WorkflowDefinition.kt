package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/** Whether a workflow definition is system-wide or owned by a single org. */
enum class WorkflowScope
{
    APP,
    ORG,
}

/**
 * A reusable workflow template. Triggered by an event name (e.g. `session.approval_requested`)
 * and described by a JSON `stepsJson` document conforming to
 * [com.docuhyphen.app.api.service.workflow.WorkflowSpec].
 *
 * The combination `(name, version)` is unique, bumping `version` is how a definition
 * evolves without rewriting in-flight instances (which always capture
 * `definition_version` for replay-safety).
 */
@Entity
@Serializable
@Table(name = "workflow_definition")
class WorkflowDefinition
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "version", nullable = false)
    var version: Int = 1

    @Column(name = "scope", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scope: WorkflowScope = WorkflowScope.APP

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "trigger_event", nullable = false, length = 128)
    lateinit var triggerEvent: String

    @Column(name = "steps_json", nullable = false, columnDefinition = "text")
    lateinit var stepsJson: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    /** JSON array of free-form industry tag strings, e.g. `["legal","hr"]`. */
    @Column(name = "general_tags", nullable = false, columnDefinition = "text")
    var generalTags: String = "[]"

    @Column(name = "summary", nullable = true, length = 512)
    var summary: String? = null

    /**
     * True once an org admin explicitly publishes the definition.
     * Non-admin org members can only see definitions where isPublished = true.
     * Has no effect on APP-scoped templates (managed by app admins directly).
     */
    @Column(name = "is_published", nullable = false)
    var isPublished: Boolean = false

    /** True for platform-bundled templates seeded via Flyway; false for org-created definitions. */
    @Column(name = "is_template", nullable = false)
    var isTemplate: Boolean = false

    /** Set when this definition was cloned from a platform template. */
    @Column(name = "source_template_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var sourceTemplateId: UUID? = null

    @Column(name = "created_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

