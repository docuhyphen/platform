package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Stable runtime occurrence of a Template Requirement inside one request. Revisions state what
 * policy was effective for this occurrence over time.
 */
@Entity
@Table(name = "information_request_requirement")
class InformationRequestRequirement
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "source_template_version_id", nullable = false)
    lateinit var sourceTemplateVersionId: UUID

    @Column(name = "source_template_requirement_id", nullable = false)
    lateinit var sourceTemplateRequirementId: UUID

    @Column(name = "source_template_binding_id", nullable = false)
    lateinit var sourceTemplateBindingId: UUID

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
