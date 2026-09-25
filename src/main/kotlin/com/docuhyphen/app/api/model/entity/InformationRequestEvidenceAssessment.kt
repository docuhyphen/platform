package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "information_request_evidence_assessment")
class InformationRequestEvidenceAssessment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "evidence_version_id", nullable = false)
    lateinit var evidenceVersionId: UUID

    @Column(name = "assessment_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var assessmentKind: InformationRequestEvidenceAssessmentKind

    @Column(name = "outcome", nullable = false, length = 32)
    lateinit var outcome: String

    @Column(name = "content_hash_algorithm", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var contentHashAlgorithm: DocumentVersionContentHashAlgorithm

    @Column(name = "content_hash", nullable = false, length = 128)
    lateinit var contentHash: String

    @Column(name = "content_length", nullable = false)
    var contentLength: Long = 0

    @Column(name = "detected_media_type")
    var detectedMediaType: String? = null

    @Column(name = "page_count")
    var pageCount: Int? = null

    @Column(name = "engine_name", length = 128)
    var engineName: String? = null

    @Column(name = "engine_version", length = 64)
    var engineVersion: String? = null

    @Column(name = "signature_version", length = 128)
    var signatureVersion: String? = null

    @Column(name = "signatures_published_at")
    var signaturesPublishedAt: Timestamp? = null

    @Column(name = "production_eligible", nullable = false)
    var productionEligible: Boolean = false

    @Column(name = "reused_assessment_id")
    var reusedAssessmentId: UUID? = null

    @Column(name = "detail", length = 512)
    var detail: String? = null

    @Column(name = "assessed_at", nullable = false)
    var assessedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
