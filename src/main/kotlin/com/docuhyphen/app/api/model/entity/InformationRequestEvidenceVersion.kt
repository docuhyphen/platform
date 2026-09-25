package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One recorded attempt at an evidence artifact. Rows are only ever appended: the version number
 * rises by one for each attempt and the stored source is never rewritten.
 */
@Entity
@Table(name = "information_request_evidence_version")
class InformationRequestEvidenceVersion
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "evidence_artifact_id", nullable = false)
    lateinit var evidenceArtifactId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "version_number", nullable = false)
    var versionNumber: Int = 1

    @Column(name = "source_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var sourceKind: InformationRequestEvidenceSourceKind

    @Column(name = "document_version_id")
    var documentVersionId: UUID? = null

    @Column(name = "external_reference_type", length = 64)
    var externalReferenceType: String? = null

    @Column(name = "external_reference_value", length = 512)
    var externalReferenceValue: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var createdByPrincipalKind: PrincipalKind

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "created_by_session_ref")
    var createdBySessionRef: String? = null

    @Column(name = "declared_file_name")
    var declaredFileName: String? = null

    @Column(name = "declared_media_type")
    var declaredMediaType: String? = null

    @Column(name = "issuer")
    var issuer: String? = null

    @Column(name = "jurisdiction", length = 64)
    var jurisdiction: String? = null

    @Column(name = "language", length = 35)
    var language: String? = null

    @Column(name = "issued_on")
    var issuedOn: LocalDate? = null

    @Column(name = "expires_on")
    var expiresOn: LocalDate? = null

    @Column(name = "coverage_starts_on")
    var coverageStartsOn: LocalDate? = null

    @Column(name = "coverage_ends_on")
    var coverageEndsOn: LocalDate? = null

    @Column(name = "certification_reference")
    var certificationReference: String? = null

    @Column(name = "signature_reference")
    var signatureReference: String? = null

    constructor()
}
