package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class InformationRequestPrivacyRequestKind
{
    ACCESS,
    EXPORT,
    CORRECTION,
    RESTRICTION,
    DELETION,
}

enum class InformationRequestPrivacyRequestState
{
    RECORDED,
    COMPLETED,
    REFUSED,
}

enum class InformationRequestPrivacyTargetOutcome
{
    EXPORTED,
    CORRECTED,
    RESTRICTED,
    DISPOSAL_CLAIMED,
    REFUSED,
}

@Entity
@Table(name = "information_request_privacy_request")
class InformationRequestPrivacyRequest
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Column(name = "subject_identity_ref_id", nullable = false)
    lateinit var subjectIdentityRefId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "request_kind", nullable = false, length = 32)
    var requestKind: InformationRequestPrivacyRequestKind = InformationRequestPrivacyRequestKind.ACCESS

    @Column(name = "purpose_key", nullable = false, length = 128)
    lateinit var purposeKey: String

    @Column(name = "policy_basis_key", nullable = false, length = 128)
    lateinit var policyBasisKey: String

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    var state: InformationRequestPrivacyRequestState = InformationRequestPrivacyRequestState.RECORDED

    @Column(name = "refusal_code", length = 128)
    var refusalCode: String? = null

    @Column(name = "refusal_detail", length = 512)
    var refusalDetail: String? = null

    @Column(name = "record_export_id")
    var recordExportId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    var recordedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "completed_at")
    var completedAt: Timestamp? = null

    @Column(name = "request_revision", nullable = false)
    var requestRevision: Long = 1
}

@Entity
@Table(name = "information_request_subject_restriction")
class InformationRequestSubjectRestriction
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Column(name = "subject_identity_ref_id", nullable = false)
    lateinit var subjectIdentityRefId: UUID

    @Column(name = "privacy_request_id", nullable = false)
    lateinit var privacyRequestId: UUID

    @Column(name = "restricted_at", nullable = false)
    var restrictedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "lifted_at")
    var liftedAt: Timestamp? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "lifted_by_principal_kind", length = 32)
    var liftedByPrincipalKind: PrincipalKind? = null

    @Column(name = "lifted_by_principal_id")
    var liftedByPrincipalId: UUID? = null

    @Column(name = "lift_reason_code", length = 128)
    var liftReasonCode: String? = null
}

@Entity
@Table(name = "information_request_item_correction")
class InformationRequestItemCorrection
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "submission_item_id", nullable = false)
    lateinit var submissionItemId: UUID

    @Column(name = "privacy_request_id")
    var privacyRequestId: UUID? = null

    @Column(name = "corrected_value_json", columnDefinition = "text")
    var correctedValueJson: String? = null

    @Column(name = "corrected_narrative", columnDefinition = "text")
    var correctedNarrative: String? = null

    @Column(name = "reason_code", nullable = false, length = 128)
    lateinit var reasonCode: String

    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    var recordedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())
}
