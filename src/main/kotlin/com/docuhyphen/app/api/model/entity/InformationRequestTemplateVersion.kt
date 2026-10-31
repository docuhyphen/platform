package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

    @Column(name = "schema_version_id", nullable = true)
    var schemaVersionId: UUID? = null

    @Column(name = "submission_mode", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE

    @Column(name = "submission_stage_ordering", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var submissionStageOrdering: InformationRequestSubmissionStageOrdering =
        InformationRequestSubmissionStageOrdering.ANY_ORDER

    @Column(name = "review_stage_ordering", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var reviewStageOrdering: InformationRequestReviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL

    @Column(name = "fact_reuse_purpose_key", nullable = true, length = 128)
    var factReusePurposeKey: String? = null

    @Column(name = "revision", nullable = false)
    var revision: Long = 0

    @Column(name = "published_at", nullable = true)
    var publishedAt: Timestamp? = null

    @Column(name = "published_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var publishedByPrincipalKind: PrincipalKind? = null

    @Column(name = "published_by_principal_id")
    var publishedByPrincipalId: UUID? = null

    @Column(name = "retired_at", nullable = true)
    var retiredAt: Timestamp? = null

    @Column(name = "retired_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var retiredByPrincipalKind: PrincipalKind? = null

    @Column(name = "retired_by_principal_id")
    var retiredByPrincipalId: UUID? = null

    @Column(name = "created_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var createdByPrincipalKind: PrincipalKind? = null

    @Column(name = "created_by_principal_id")
    var createdByPrincipalId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
