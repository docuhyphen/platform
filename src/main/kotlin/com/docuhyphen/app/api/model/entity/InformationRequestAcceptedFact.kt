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
@Table(name = "information_request_accepted_fact")
class InformationRequestAcceptedFact
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var ownerType: InformationRequestOwnerType = InformationRequestOwnerType.ORGANIZATION

    @Column(name = "owner_organization_id")
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_user_id")
    var ownerUserId: UUID? = null

    @Column(name = "subject_identity_ref_id", nullable = false)
    lateinit var subjectIdentityRefId: UUID

    @Column(name = "purpose_key", nullable = false, length = 128)
    lateinit var purposeKey: String

    @Column(name = "policy_basis_key", nullable = false, length = 128)
    lateinit var policyBasisKey: String

    @Column(name = "field_definition_id", nullable = false)
    lateinit var fieldDefinitionId: UUID

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

    @Column(name = "source_response_id", nullable = false)
    lateinit var sourceResponseId: UUID

    @Column(name = "source_response_revision", nullable = false)
    var sourceResponseRevision: Long = 1

    @Column(name = "source_field_value_revision_id", nullable = false)
    lateinit var sourceFieldValueRevisionId: UUID

    @Column(name = "source_review_id")
    var sourceReviewId: UUID? = null

    @Column(name = "visibility", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var visibility: InformationRequestAcceptedFactVisibility = InformationRequestAcceptedFactVisibility.REQUESTING_SIDE

    @Column(name = "confidence", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var confidence: InformationRequestAcceptedFactConfidence = InformationRequestAcceptedFactConfidence.DECLARED

    @Column(name = "valid_from", nullable = false)
    var validFrom: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "valid_to")
    var validTo: Timestamp? = null

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "supersedes_fact_id")
    var supersedesFactId: UUID? = null

    @Column(name = "conflict_state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var conflictState: InformationRequestAcceptedFactConflictState = InformationRequestAcceptedFactConflictState.NONE

    @Column(name = "conflicting_fact_id")
    var conflictingFactId: UUID? = null

    @Column(name = "promoted_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var promotedByPrincipalKind: PrincipalKind

    @Column(name = "promoted_by_principal_id", nullable = false)
    lateinit var promotedByPrincipalId: UUID

    @Column(name = "promoted_at", nullable = false)
    var promotedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_accepted_fact_revocation")
class InformationRequestAcceptedFactRevocation
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "fact_id", nullable = false)
    lateinit var factId: UUID

    @Column(name = "reason_code", nullable = false, length = 128)
    lateinit var reasonCode: String

    @Column(name = "narrative")
    var narrative: String? = null

    @Column(name = "revoked_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var revokedByPrincipalKind: PrincipalKind

    @Column(name = "revoked_by_principal_id", nullable = false)
    lateinit var revokedByPrincipalId: UUID

    @Column(name = "revoked_at", nullable = false)
    var revokedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_business_decision")
class InformationRequestBusinessDecision
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "owning_process_key", nullable = false, length = 128)
    lateinit var owningProcessKey: String

    @Column(name = "outcome_code", nullable = false, length = 128)
    lateinit var outcomeCode: String

    @Column(name = "reason_reference", length = 512)
    var reasonReference: String? = null

    @Column(name = "external_reference", length = 512)
    var externalReference: String? = null

    @Column(name = "kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var kind: InformationRequestBusinessDecisionKind = InformationRequestBusinessDecisionKind.ORIGINAL

    @Column(name = "prior_decision_id")
    var priorDecisionId: UUID? = null

    @Column(name = "decision_revision", nullable = false)
    var decisionRevision: Int = 1

    @Column(name = "decided_at", nullable = false)
    var decidedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
