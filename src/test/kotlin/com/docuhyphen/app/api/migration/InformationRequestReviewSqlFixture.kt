package com.docuhyphen.app.api.migration

import java.sql.Connection
import java.util.UUID

internal class ReviewSqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
)
{
    val reviewerPartyId: UUID = UUID.randomUUID()
    val reviewerUserId: UUID = UUID.randomUUID()
    val packageId: UUID = UUID.randomUUID()
    val documentItemId: UUID = UUID.randomUUID()
    val attestationItemId: UUID = UUID.randomUUID()
    val evidenceMemberId: UUID = UUID.randomUUID()

    init
    {
        runtime.insertActingParty(reviewerPartyId, "REVIEWER", reviewerUserId)
        runtime.insertPackage(packageId, 1, reviewRequired = true)
        runtime.insertItem(
            documentItemId,
            packageId,
            runtime.documentRequirementId,
            runtime.documentRevisionId,
            runtime.documentBindingId,
            "DOCUMENT",
            runtime.documentResponseId,
        )
        runtime.insertItem(
            attestationItemId,
            packageId,
            runtime.attestationRequirementId,
            runtime.attestationRevisionId,
            runtime.attestationBindingId,
            "RESPONSE_ATTESTATION",
        )
        runtime.insertEvidenceMember(evidenceMemberId, packageId, documentItemId)
    }

    @Suppress("LongParameterList")
    fun insertReview(
        id: UUID,
        number: Int,
        kind: String = "INITIAL",
        state: String = "PENDING",
        priorReviewId: UUID? = null,
        forPackage: UUID = packageId,
        templateVersionId: UUID = runtime.template.versionId,
        reason: String? = null,
    )
    {
        val settled = state !in setOf("PENDING", "IN_REVIEW")
        execute(
            connection,
            """
            INSERT INTO information_request_review
                (id, information_request_id, package_id, review_number, kind, prior_review_id, template_version_id,
                 state, review_revision, opening_reason, opened_by_principal_kind, opened_by_principal_id, opened_at,
                 settled_at, settled_by_principal_kind, settled_by_principal_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'USER', ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            runtime.requestId,
            forPackage,
            number,
            kind,
            priorReviewId,
            templateVersionId,
            state,
            reason,
            runtime.contributorUserId,
            runtime.template.now,
            if (settled) runtime.template.now else null,
            if (settled) "USER" else null,
            if (settled) reviewerUserId else null,
        )
    }

    fun insertAssignment(
        id: UUID,
        reviewId: UUID,
        stageId: UUID = runtime.reviewStageId(),
        stageKey: String = "review",
        partyId: UUID = reviewerPartyId,
        principalId: UUID = reviewerUserId,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_review_assignment
                (id, review_id, information_request_id, template_review_stage_id, stage_key, reviewer_party_id,
                 reviewer_principal_kind, reviewer_principal_id, state, draft_revision, assigned_by_principal_kind,
                 assigned_by_principal_id, assigned_at)
            VALUES (?, ?, ?, ?, ?, ?, 'USER', ?, 'ACTIVE', 1, 'USER', ?, ?)
            """.trimIndent(),
            id,
            reviewId,
            runtime.requestId,
            stageId,
            stageKey,
            partyId,
            principalId,
            runtime.contributorUserId,
            runtime.template.now,
        )
    }

    @Suppress("LongParameterList")
    fun insertDecision(
        id: UUID,
        reviewId: UUID,
        sequence: Int,
        kind: String = "REVIEWER",
        assignmentId: UUID? = null,
        outcome: String = "SATISFIED",
        narrative: String? = null,
        itemId: UUID = documentItemId,
        requirementId: UUID = runtime.documentRequirementId,
        carriedFrom: UUID? = null,
        forPackage: UUID = packageId,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_review_decision
                (id, review_id, information_request_id, package_id, submission_item_id, requirement_id,
                 template_review_stage_id, stage_key, kind, assignment_id, carried_from_decision_id, outcome, narrative,
                 decided_by_principal_kind, decided_by_principal_id, decided_at, sequence_number)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'review', ?, ?, ?, ?, ?, 'USER', ?, ?, ?)
            """.trimIndent(),
            id,
            reviewId,
            runtime.requestId,
            forPackage,
            itemId,
            requirementId,
            runtime.reviewStageId(),
            kind,
            assignmentId,
            carriedFrom,
            outcome,
            narrative,
            reviewerUserId,
            runtime.template.now,
            sequence,
        )
    }

    @Suppress("LongParameterList")
    fun insertFinding(
        id: UUID,
        reviewId: UUID,
        sequence: Int,
        evidenceVersionId: UUID? = null,
        scope: String = "RESPONSE",
        reasonCode: String = "record.incomplete",
        visibility: String = "RESPONDENT_VISIBLE",
        itemId: UUID = documentItemId,
        requirementId: UUID = runtime.documentRequirementId,
        retests: UUID? = null,
        retestResult: String? = null,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_review_finding
                (id, review_id, information_request_id, package_id, submission_item_id, requirement_id,
                 evidence_version_id, reason_code, narrative, severity, visibility, correction_scope,
                 retests_finding_id, retest_result, recorded_by_principal_kind, recorded_by_principal_id, recorded_at,
                 sequence_number)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'The recorded item is incomplete', 'MAJOR', ?, ?, ?, ?, 'USER', ?, ?, ?)
            """.trimIndent(),
            id,
            reviewId,
            runtime.requestId,
            packageId,
            itemId,
            requirementId,
            evidenceVersionId,
            reasonCode,
            visibility,
            scope,
            retests,
            retestResult,
            reviewerUserId,
            runtime.template.now,
            sequence,
        )
    }

    fun insertCorrection(id: UUID, reviewId: UUID, forPackage: UUID = packageId)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_correction
                (id, information_request_id, review_id, package_id, state, opened_at)
            VALUES (?, ?, ?, ?, 'OPEN', ?)
            """.trimIndent(),
            id,
            runtime.requestId,
            reviewId,
            forPackage,
            runtime.template.now,
        )
    }

    fun insertCorrectionItem(id: UUID, correctionId: UUID, itemId: UUID = documentItemId, requirementId: UUID = runtime.documentRequirementId)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_correction_item
                (id, correction_id, information_request_id, submission_item_id, requirement_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            correctionId,
            runtime.requestId,
            itemId,
            requirementId,
        )
    }

    fun insertCorrectionEvidence(correctionId: UUID, correctionItemId: UUID, versionId: UUID = runtime.evidenceVersionId)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_correction_evidence
                (id, correction_id, correction_item_id, information_request_id, evidence_artifact_id, evidence_version_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            correctionId,
            correctionItemId,
            runtime.requestId,
            runtime.artifactId,
            versionId,
        )
    }

    @Suppress("LongParameterList")
    fun insertComment(
        id: UUID,
        reviewId: UUID,
        sequence: Int,
        role: String = "REVIEWER",
        visibility: String = "RESPONDENT_VISIBLE",
        findingId: UUID? = null,
        replyTo: UUID? = null,
        itemId: UUID = documentItemId,
        requirementId: UUID = runtime.documentRequirementId,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_review_comment
                (id, information_request_id, review_id, package_id, submission_item_id, requirement_id, finding_id,
                 reply_to_comment_id, author_role, visibility, body, author_principal_kind, author_principal_id,
                 created_at, sequence_number)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'A note about the recorded item', 'USER', ?, ?, ?)
            """.trimIndent(),
            id,
            runtime.requestId,
            reviewId,
            packageId,
            itemId,
            requirementId,
            findingId,
            replyTo,
            role,
            visibility,
            reviewerUserId,
            runtime.template.now,
            sequence,
        )
    }
}
