package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "information_request_response_archive")
class InformationRequestResponseArchive {
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "response_id", nullable = false)
    lateinit var responseId: UUID

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "condition_rule_key", nullable = false, length = 128)
    lateinit var conditionRuleKey: String

    @Column(name = "response_revision", nullable = false)
    var responseRevision: Long = 1

    @Column(name = "disposition", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var disposition: InformationRequestResponseDisposition = InformationRequestResponseDisposition.NOT_ANSWERED

    @Column(name = "narrative", columnDefinition = "text")
    var narrative: String? = null

    @Column(name = "field_contract_id")
    var fieldContractId: UUID? = null

    @Column(name = "field_value_type", length = 32)
    @Enumerated(EnumType.STRING)
    var fieldValueType: FieldValueType? = null

    @Column(name = "field_value_json", columnDefinition = "text")
    var fieldValueJson: String? = null

    @Column(name = "field_value_set_etag", length = 128)
    var fieldValueSetETag: String? = null

    @Column(name = "evidence_count", nullable = false)
    var evidenceCount: Int = 0

    @Column(name = "archived_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var archivedByPrincipalKind: PrincipalKind

    @Column(name = "archived_by_principal_id", nullable = false)
    lateinit var archivedByPrincipalId: UUID

    @Column(name = "archived_by_session_ref", length = 64)
    var archivedBySessionRef: String? = null

    @Column(name = "archived_at", nullable = false)
    var archivedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_response_archive_evidence")
class InformationRequestResponseArchiveEvidence {
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "archive_id", nullable = false)
    lateinit var archiveId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "evidence_artifact_id", nullable = false)
    lateinit var evidenceArtifactId: UUID

    @Column(name = "evidence_version_id", nullable = false)
    lateinit var evidenceVersionId: UUID

    @Column(name = "evidence_version_number", nullable = false)
    var evidenceVersionNumber: Int = 1

    constructor()
}
