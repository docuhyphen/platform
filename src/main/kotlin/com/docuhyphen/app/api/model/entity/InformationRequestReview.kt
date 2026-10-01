package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "information_request_review")
class InformationRequestReview
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "review_number", nullable = false)
    var reviewNumber: Int = 1

    @Column(name = "kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var kind: InformationRequestReviewKind = InformationRequestReviewKind.INITIAL

    @Column(name = "prior_review_id")
    var priorReviewId: UUID? = null

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var state: InformationRequestReviewState = InformationRequestReviewState.PENDING

    @Column(name = "review_revision", nullable = false)
    var reviewRevision: Long = 1

    @Column(name = "opening_reason")
    var openingReason: String? = null

    @Column(name = "opened_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var openedByPrincipalKind: PrincipalKind

    @Column(name = "opened_by_principal_id", nullable = false)
    lateinit var openedByPrincipalId: UUID

    @Column(name = "opened_at", nullable = false)
    var openedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "settled_at")
    var settledAt: Timestamp? = null

    @Column(name = "settled_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var settledByPrincipalKind: PrincipalKind? = null

    @Column(name = "settled_by_principal_id")
    var settledByPrincipalId: UUID? = null

    constructor()
}

@Entity
@Table(name = "information_request_review_assignment")
class InformationRequestReviewAssignment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "review_id", nullable = false)
    lateinit var reviewId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "template_review_stage_id", nullable = false)
    lateinit var templateReviewStageId: UUID

    @Column(name = "stage_key", nullable = false, length = 128)
    lateinit var stageKey: String

    @Column(name = "reviewer_party_id", nullable = false)
    lateinit var reviewerPartyId: UUID

    @Column(name = "reviewer_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var reviewerPrincipalKind: PrincipalKind

    @Column(name = "reviewer_principal_id", nullable = false)
    lateinit var reviewerPrincipalId: UUID

    @Column(name = "delegated_from_assignment_id")
    var delegatedFromAssignmentId: UUID? = null

    @Column(name = "due_at")
    var dueAt: Timestamp? = null

    @Column(name = "state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var state: InformationRequestReviewAssignmentState = InformationRequestReviewAssignmentState.ACTIVE

    @Column(name = "draft_revision", nullable = false)
    var draftRevision: Long = 1

    @Column(name = "assigned_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var assignedByPrincipalKind: PrincipalKind

    @Column(name = "assigned_by_principal_id", nullable = false)
    lateinit var assignedByPrincipalId: UUID

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "change_reason_code", length = 128)
    var changeReasonCode: String? = null

    @Column(name = "change_narrative")
    var changeNarrative: String? = null

    @Column(name = "changed_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var changedByPrincipalKind: PrincipalKind? = null

    @Column(name = "changed_by_principal_id")
    var changedByPrincipalId: UUID? = null

    @Column(name = "changed_at")
    var changedAt: Timestamp? = null

    @Column(name = "decided_at")
    var decidedAt: Timestamp? = null

    constructor()
}

@Entity
@Table(name = "information_request_review_draft_item")
class InformationRequestReviewDraftItem
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "assignment_id", nullable = false)
    lateinit var assignmentId: UUID

    @Column(name = "review_id", nullable = false)
    lateinit var reviewId: UUID

    @Column(name = "submission_item_id", nullable = false)
    lateinit var submissionItemId: UUID

    @Column(name = "outcome", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var outcome: InformationRequestReviewOutcome

    @Column(name = "narrative")
    var narrative: String? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_review_decision")
class InformationRequestReviewDecision
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "review_id", nullable = false)
    lateinit var reviewId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "submission_item_id", nullable = false)
    lateinit var submissionItemId: UUID

    @Column(name = "requirement_id", nullable = false)
    lateinit var requirementId: UUID

    @Column(name = "template_review_stage_id", nullable = false)
    lateinit var templateReviewStageId: UUID

    @Column(name = "stage_key", nullable = false, length = 128)
    lateinit var stageKey: String

    @Column(name = "kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var kind: InformationRequestReviewDecisionKind = InformationRequestReviewDecisionKind.REVIEWER

    @Column(name = "assignment_id")
    var assignmentId: UUID? = null

    @Column(name = "carried_from_decision_id")
    var carriedFromDecisionId: UUID? = null

    @Column(name = "outcome", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var outcome: InformationRequestReviewOutcome

    @Column(name = "narrative")
    var narrative: String? = null

    @Column(name = "decided_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var decidedByPrincipalKind: PrincipalKind

    @Column(name = "decided_by_principal_id", nullable = false)
    lateinit var decidedByPrincipalId: UUID

    @Column(name = "decided_at", nullable = false)
    var decidedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Int = 1

    constructor()
}

@Entity
@Table(name = "information_request_review_finding")
class InformationRequestReviewFinding
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "review_id", nullable = false)
    lateinit var reviewId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "submission_item_id", nullable = false)
    lateinit var submissionItemId: UUID

    @Column(name = "requirement_id", nullable = false)
    lateinit var requirementId: UUID

    @Column(name = "evidence_version_id")
    var evidenceVersionId: UUID? = null

    @Column(name = "assignment_id")
    var assignmentId: UUID? = null

    @Column(name = "reason_code", nullable = false, length = 128)
    lateinit var reasonCode: String

    @Column(name = "narrative", nullable = false)
    lateinit var narrative: String

    @Column(name = "severity", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var severity: InformationRequestFindingSeverity

    @Column(name = "visibility", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var visibility: InformationRequestReviewVisibility

    @Column(name = "correction_scope", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var correctionScope: InformationRequestFindingCorrectionScope

    @Column(name = "retests_finding_id")
    var retestsFindingId: UUID? = null

    @Column(name = "retest_result", length = 32)
    @Enumerated(EnumType.STRING)
    var retestResult: InformationRequestRetestResult? = null

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Int = 1

    constructor()
}

@Entity
@Table(name = "information_request_correction")
class InformationRequestCorrection
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "review_id", nullable = false)
    lateinit var reviewId: UUID

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var state: InformationRequestCorrectionState = InformationRequestCorrectionState.OPEN

    @Column(name = "opened_at", nullable = false)
    var openedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "closed_at")
    var closedAt: Timestamp? = null

    @Column(name = "resubmitted_package_id")
    var resubmittedPackageId: UUID? = null

    constructor()
}

@Entity
@Table(name = "information_request_correction_item")
class InformationRequestCorrectionItem
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "correction_id", nullable = false)
    lateinit var correctionId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "submission_item_id", nullable = false)
    lateinit var submissionItemId: UUID

    @Column(name = "requirement_id", nullable = false)
    lateinit var requirementId: UUID

    constructor()
}

@Entity
@Table(name = "information_request_correction_evidence")
class InformationRequestCorrectionEvidence
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "correction_id", nullable = false)
    lateinit var correctionId: UUID

    @Column(name = "correction_item_id", nullable = false)
    lateinit var correctionItemId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "evidence_artifact_id", nullable = false)
    lateinit var evidenceArtifactId: UUID

    @Column(name = "evidence_version_id", nullable = false)
    lateinit var evidenceVersionId: UUID

    constructor()
}

@Entity
@Table(name = "information_request_review_comment")
class InformationRequestReviewComment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "review_id", nullable = false)
    lateinit var reviewId: UUID

    @Column(name = "package_id", nullable = false)
    lateinit var packageId: UUID

    @Column(name = "submission_item_id", nullable = false)
    lateinit var submissionItemId: UUID

    @Column(name = "requirement_id", nullable = false)
    lateinit var requirementId: UUID

    @Column(name = "finding_id")
    var findingId: UUID? = null

    @Column(name = "reply_to_comment_id")
    var replyToCommentId: UUID? = null

    @Column(name = "author_role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var authorRole: InformationRequestReviewCommentRole

    @Column(name = "visibility", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var visibility: InformationRequestReviewVisibility

    @Column(name = "body", nullable = false)
    lateinit var body: String

    @Column(name = "author_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var authorPrincipalKind: PrincipalKind

    @Column(name = "author_principal_id", nullable = false)
    lateinit var authorPrincipalId: UUID

    @Column(name = "author_session_ref", length = 64)
    var authorSessionRef: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Int = 1

    constructor()
}

@Entity
@Table(name = "information_request_review_remediation")
class InformationRequestReviewRemediation
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "correction_id", nullable = false)
    lateinit var correctionId: UUID

    @Column(name = "finding_id", nullable = false)
    lateinit var findingId: UUID

    @Column(name = "remediated_by_package_id", nullable = false)
    lateinit var remediatedByPackageId: UUID

    @Column(name = "remediated_by_item_id", nullable = false)
    lateinit var remediatedByItemId: UUID

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
