package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * One frozen answer to an [InformationRequestTemplateDefinition]. Publishing a changed draft
 * creates a new Version; it never rewrites a published one, because every request that pins a
 * Version must keep resolving exactly the configuration it was issued against. Sections live in
 * [InformationRequestTemplateSection] and requirement placements in
 * [InformationRequestTemplateRequirementBinding].
 */
@Entity
@Table(name = "information_request_template_version")
class InformationRequestTemplateVersion
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_definition_id", nullable = false)
    lateinit var templateDefinitionId: UUID

    @Column(name = "version_number", nullable = false)
    var versionNumber: Int = 1

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var status: InformationRequestTemplateStatus = InformationRequestTemplateStatus.DRAFT

    /**
     * The exact published Schema Version that typed Field requirements resolve against, or null
     * when this Version requests no typed data. Whether it is required is decided where requirement
     * types are known, not here.
     */
    @Column(name = "schema_version_id", nullable = true)
    var schemaVersionId: UUID? = null

    @Column(name = "submission_mode", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE

    @Column(name = "submission_stage_ordering", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var submissionStageOrdering: InformationRequestSubmissionStageOrdering =
        InformationRequestSubmissionStageOrdering.ANY_ORDER

    @Column(name = "published_at", nullable = true)
    var publishedAt: Timestamp? = null

    @Column(name = "published_by_app_user_id", nullable = true)
    var publishedByAppUserId: UUID? = null

    @Column(name = "retired_at", nullable = true)
    var retiredAt: Timestamp? = null

    @Column(name = "retired_by_app_user_id", nullable = true)
    var retiredByAppUserId: UUID? = null

    @Column(name = "created_by_app_user_id", nullable = true)
    var createdByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
