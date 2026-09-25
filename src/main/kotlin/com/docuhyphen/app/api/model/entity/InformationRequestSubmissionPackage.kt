package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "information_request_submission_package")
class InformationRequestSubmissionPackage
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "package_number", nullable = false)
    var packageNumber: Int = 1

    @Column(name = "stage_key", length = 128)
    var stageKey: String? = null

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "schema_version_id")
    var schemaVersionId: UUID? = null

    @Column(name = "content_hash_sha256", nullable = false, length = 64)
    lateinit var contentHashSha256: String

    @Column(name = "manifest_hash_sha256", nullable = false, length = 64)
    lateinit var manifestHashSha256: String

    @Column(name = "review_required", nullable = false)
    var reviewRequired: Boolean = false

    @Column(name = "completes_request", nullable = false)
    var completesRequest: Boolean = false

    @Column(name = "previous_package_id")
    var previousPackageId: UUID? = null

    @Column(name = "submitted_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var submittedByPrincipalKind: PrincipalKind

    @Column(name = "submitted_by_principal_id", nullable = false)
    lateinit var submittedByPrincipalId: UUID

    @Column(name = "submitted_by_session_ref", length = 64)
    var submittedBySessionRef: String? = null

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_submission_item")
class InformationRequestSubmissionItem
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "requirement_revision_id", nullable = false)
    lateinit var requirementRevisionId: UUID

    @Column(name = "template_binding_id", nullable = false)
    lateinit var templateBindingId: UUID

    @Column(name = "requirement_key", nullable = false, length = 128)
    lateinit var requirementKey: String

    @Column(name = "requirement_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var requirementType: InformationRequestRequirementType

    @Column(name = "occurrence_path", nullable = false, length = 512)
    lateinit var occurrencePath: String

    @Column(name = "completeness_state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var completenessState: InformationRequestCompletenessItemState

    @Column(name = "disposition", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var disposition: InformationRequestResponseDisposition = InformationRequestResponseDisposition.NOT_ANSWERED

    @Column(name = "narrative", columnDefinition = "text")
    var narrative: String? = null

    @Column(name = "response_id")
    var responseId: UUID? = null

    @Column(name = "response_revision")
    var responseRevision: Long? = null

    @Column(name = "responded_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var respondedByPrincipalKind: PrincipalKind? = null

    @Column(name = "responded_by_principal_id")
    var respondedByPrincipalId: UUID? = null

    @Column(name = "responded_by_session_ref", length = 64)
    var respondedBySessionRef: String? = null

    @Column(name = "field_value_set_id")
    var fieldValueSetId: UUID? = null

    @Column(name = "field_value_revision_id")
    var fieldValueRevisionId: UUID? = null

    @Column(name = "evidence_state", length = 32)
    var evidenceState: String? = null

    @Column(name = "attestation_state", length = 32)
    var attestationState: String? = null

    @Column(name = "item_hash_sha256", nullable = false, length = 64)
    lateinit var itemHashSha256: String

    constructor()
}

@Entity
@Table(name = "information_request_submission_evidence")
class InformationRequestSubmissionEvidence
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "item_id", nullable = false)
    lateinit var itemId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "evidence_artifact_id", nullable = false)
    lateinit var evidenceArtifactId: UUID

    @Column(name = "evidence_version_id", nullable = false)
    lateinit var evidenceVersionId: UUID

    @Column(name = "evidence_version_number", nullable = false)
    var evidenceVersionNumber: Int = 1

    @Column(name = "document_version_id")
    var documentVersionId: UUID? = null

    @Column(name = "content_hash_algorithm", length = 16)
    var contentHashAlgorithm: String? = null

    @Column(name = "content_hash", length = 128)
    var contentHash: String? = null

    @Column(name = "content_length")
    var contentLength: Long? = null

    @Column(name = "content_verification", length = 16)
    var contentVerification: String? = null

    @Column(name = "conformance", nullable = false, length = 32)
    lateinit var conformance: String

    @Column(name = "inspection_assessment_id")
    var inspectionAssessmentId: UUID? = null

    @Column(name = "malware_assessment_id")
    var malwareAssessmentId: UUID? = null

    constructor()
}

@Entity
@Table(name = "information_request_submission_supporting_link")
class InformationRequestSubmissionSupportingLink
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "supporting_evidence_link_id", nullable = false)
    lateinit var supportingEvidenceLinkId: UUID

    @Column(name = "supported_requirement_id", nullable = false)
    lateinit var supportedRequirementId: UUID

    @Column(name = "supporting_requirement_id", nullable = false)
    lateinit var supportingRequirementId: UUID

    constructor()
}

@Entity
@Table(name = "information_request_submission_attestation")
class InformationRequestSubmissionAttestation
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "attestation_requirement_id", nullable = false)
    lateinit var attestationRequirementId: UUID

    @Column(name = "requirement_revision_id", nullable = false)
    lateinit var requirementRevisionId: UUID

    @Column(name = "stage_key", length = 128)
    var stageKey: String? = null

    @Column(name = "party_id", nullable = false)
    lateinit var partyId: UUID

    @Column(name = "party_role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var partyRole: InformationRequestContributorRole

    @Column(name = "principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var principalKind: PrincipalKind

    @Column(name = "principal_id", nullable = false)
    lateinit var principalId: UUID

    @Column(name = "session_ref", length = 64)
    var sessionRef: String? = null

    @Column(name = "delegated_authority_id")
    var delegatedAuthorityId: UUID? = null

    @Column(name = "decision", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var decision: InformationRequestAttestationDecision

    @Column(name = "refusal_reason", columnDefinition = "text")
    var refusalReason: String? = null

    @Column(name = "authentication_strength", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var authenticationStrength: InformationRequestAuthenticationStrength

    @Column(name = "external_signature_reference", length = 512)
    var externalSignatureReference: String? = null

    @Column(name = "attested_content_hash_sha256", nullable = false, length = 64)
    lateinit var attestedContentHashSha256: String

    @Column(name = "statement_hash_sha256", nullable = false, length = 64)
    lateinit var statementHashSha256: String

    @Column(name = "policy_hash_sha256", nullable = false, length = 64)
    lateinit var policyHashSha256: String

    @Column(name = "attested_at", nullable = false)
    var attestedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Int = 1

    constructor()
}

enum class InformationRequestAttestationDecision
{
    ASSENTED,
    REFUSED,
}

data class InformationRequestSubmissionPackageAttestationKey(
    var packageId: UUID? = null,
    var attestationId: UUID? = null,
) : Serializable

@Entity
@IdClass(InformationRequestSubmissionPackageAttestationKey::class)
@Table(name = "information_request_submission_package_attestation")
class InformationRequestSubmissionPackageAttestation
{
    @Id
    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Id
    @Column(name = "attestation_id", nullable = false)
    lateinit var attestationId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    constructor()
}

@Entity
@Table(name = "information_request_submission_withdrawal")
class InformationRequestSubmissionWithdrawal
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "reason_code", length = 128)
    var reasonCode: String? = null

    @Column(name = "withdrawn_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var withdrawnByPrincipalKind: PrincipalKind

    @Column(name = "withdrawn_by_principal_id", nullable = false)
    lateinit var withdrawnByPrincipalId: UUID

    @Column(name = "withdrawn_by_session_ref", length = 64)
    var withdrawnBySessionRef: String? = null

    @Column(name = "withdrawn_at", nullable = false)
    var withdrawnAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
