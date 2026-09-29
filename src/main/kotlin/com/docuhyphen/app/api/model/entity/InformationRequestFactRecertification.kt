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

@Entity
@Table(name = "information_request_fact_recertification")
class InformationRequestFactRecertification
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "response_id", nullable = false)
    lateinit var responseId: UUID

    @Column(name = "response_revision", nullable = false)
    var responseRevision: Long = 1

    @Column(name = "fact_id", nullable = false)
    lateinit var factId: UUID

    @Column(name = "purpose_key", nullable = false, length = 128)
    lateinit var purposeKey: String

    @Column(name = "policy_basis_key", nullable = false, length = 128)
    lateinit var policyBasisKey: String

    @Column(name = "value_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var valueType: FieldValueType

    @Column(name = "canonical_value", nullable = false)
    lateinit var canonicalValue: String

    @Column(name = "source_information_request_id", nullable = false)
    lateinit var sourceInformationRequestId: UUID

    @Column(name = "source_package_id", nullable = false)
    lateinit var sourcePackageId: UUID

    @Column(name = "source_submission_item_id", nullable = false)
    lateinit var sourceSubmissionItemId: UUID

    @Column(name = "source_requirement_id", nullable = false)
    lateinit var sourceRequirementId: UUID

    @Column(name = "source_field_value_revision_id", nullable = false)
    lateinit var sourceFieldValueRevisionId: UUID

    @Column(name = "source_review_id")
    var sourceReviewId: UUID? = null

    @Column(name = "assented_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var assentedByPrincipalKind: PrincipalKind

    @Column(name = "assented_by_principal_id", nullable = false)
    lateinit var assentedByPrincipalId: UUID

    @Column(name = "assented_by_session_ref", length = 64)
    var assentedBySessionRef: String? = null

    @Column(name = "assented_at", nullable = false)
    var assentedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_fact_recertification_evidence")
class InformationRequestFactRecertificationEvidence
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "recertification_id", nullable = false)
    lateinit var recertificationId: UUID

    @Column(name = "evidence_version_id", nullable = false)
    lateinit var evidenceVersionId: UUID
}
