package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant

/**
 * Registry of trigger event types that can fire a [WorkflowDefinition].
 * Seeded via Flyway (V5 migration). The designer uses this table to populate
 * the trigger dropdown and to provide $subject.* field auto-complete hints.
 */
@Entity
@Serializable
@Table(name = "workflow_trigger_event_registry")
class WorkflowTriggerEventRegistry
{
    @Id
    @Column(name = "event_name", nullable = false, length = 128)
    lateinit var eventName: String

    @Column(name = "description", nullable = true, length = 512)
    var description: String? = null

    /**
     * JSON array of subject field descriptors used for auto-complete in the designer.
     * Format: `[{"name":"fieldName","type":"UUID","description":"..."}]`.
     */
    @Column(name = "subject_fields_json", nullable = false, columnDefinition = "text")
    var subjectFieldsJson: String = "[]"

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

