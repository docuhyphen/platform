package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Append-only policy snapshot for a runtime Requirement occurrence.
 */
@Entity
@Table(name = "information_request_requirement_revision")
class InformationRequestRequirementRevision
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "source_template_version_id", nullable = false)
    lateinit var sourceTemplateVersionId: UUID

    @Column(name = "source_template_requirement_id", nullable = false)
    lateinit var sourceTemplateRequirementId: UUID

    @Column(name = "source_template_binding_id", nullable = false)
    lateinit var sourceTemplateBindingId: UUID

    @Column(name = "revision_number", nullable = false)
    var revisionNumber: Int = 1

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "effective_to")
    var effectiveTo: Timestamp? = null

    @Column(name = "configuration_hash_sha256", nullable = false, length = 64)
    lateinit var configurationHashSha256: String

    @Column(name = "optimistic_version", nullable = false)
    var optimisticVersion: Long = 1

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
