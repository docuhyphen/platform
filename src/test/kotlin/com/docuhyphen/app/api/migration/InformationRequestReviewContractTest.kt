package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestReviewContractTest
{
    @Test
    fun `the request state no longer admits package review states and admits the review mutations`()
    {
        withReview { connection, review ->
            val requestId = review.runtime.requestId
            listOf("SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED").forEach { state ->
                refusedBy(connection, "ck_information_request_state") {
                    execute(connection, "UPDATE information_request SET state = ? WHERE id = ?", state, requestId)
                }
                refusedBy(connection, "ck_information_request_transition_state") {
                    insertTransition(connection, requestId, 1, "ISSUED", state, "SUBMIT")
                }
            }
            listOf(
                "ASSIGN_REVIEWER", "SAVE_REVIEW_DRAFT", "RECORD_REVIEW_DECISION", "RECORD_FINDING",
                "RECORD_REVIEW_COMMENT", "SETTLE_REVIEW", "PROMOTE_FACT", "REVOKE_FACT", "RECORD_BUSINESS_DECISION",
            ).forEachIndexed { index, mutation ->
                insertTransition(connection, requestId, index + 1, "ISSUED", "ISSUED", mutation)
            }
        }
    }

    @Test
    fun `a review cycle numbers from one, pins its package's version, and one cycle per package is open`()
    {
        withReview { connection, review ->
            val first = UUID.randomUUID()
            refusedBy(connection, "a review numbers from one without a gap") {
                review.insertReview(UUID.randomUUID(), 2)
            }
            refusedBy(connection, "ck_information_request_review_prior") {
                review.insertReview(UUID.randomUUID(), 1, kind = "RECONSIDERATION", reason = "Look again")
            }
            review.insertReview(first, 1)
            refusedBy(connection, "ux_information_request_review_open") {
                review.insertReview(UUID.randomUUID(), 2)
            }

            execute(
                connection,
                "UPDATE information_request_review SET state = 'IN_REVIEW', review_revision = 2 WHERE id = ?",
                first,
            )
            refusedBy(connection, "a review revision only moves forward") {
                execute(connection, "UPDATE information_request_review SET review_revision = 1 WHERE id = ?", first)
            }
            settle(connection, first, "REJECTED")
            refusedBy(connection, "a settled review is immutable") {
                execute(connection, "UPDATE information_request_review SET state = 'SATISFIED', review_revision = 4 WHERE id = ?", first)
            }
            refusedBy(connection, "ck_information_request_review_reason") {
                review.insertReview(UUID.randomUUID(), 2, kind = "APPEAL", priorReviewId = first)
            }
            review.insertReview(UUID.randomUUID(), 2, kind = "APPEAL", priorReviewId = first, reason = "The record was complete")
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "DELETE FROM information_request_review WHERE id = ?", first)
            }
        }
    }

    @Test
    fun `an assignment names an active reviewer party on a stage of the pinned version`()
    {
        withReview { connection, review ->
            val reviewId = UUID.randomUUID()
            review.insertReview(reviewId, 1)
            refusedBy(connection, "a review assignment names a reviewer party of its request") {
                review.insertAssignment(UUID.randomUUID(), reviewId, partyId = review.runtime.contributorPartyId)
            }
            refusedBy(connection, "a review assignment names a stage of its review's template version") {
                review.insertAssignment(UUID.randomUUID(), reviewId, stageKey = "other-stage")
            }
            val assignment = UUID.randomUUID()
            review.insertAssignment(assignment, reviewId)
            refusedBy(connection, "ux_information_request_review_assignment_active") {
                review.insertAssignment(UUID.randomUUID(), reviewId)
            }
            refusedBy(connection, "ck_information_request_review_assignment_change") {
                execute(connection, "UPDATE information_request_review_assignment SET state = 'RECUSED' WHERE id = ?", assignment)
            }
            refusedBy(connection, "ck_information_request_review_assignment_recusal") {
                execute(
                    connection,
                    """
                    UPDATE information_request_review_assignment
                    SET state = 'RECUSED', changed_at = now(), changed_by_principal_kind = 'USER', changed_by_principal_id = reviewer_principal_id
                    WHERE id = ?
                    """.trimIndent(),
                    assignment,
                )
            }
            execute(
                connection,
                """
                UPDATE information_request_review_assignment
                SET state = 'RECUSED', change_reason_code = 'declared-conflict', changed_at = now(),
                    changed_by_principal_kind = 'USER', changed_by_principal_id = reviewer_principal_id
                WHERE id = ?
                """.trimIndent(),
                assignment,
            )
            refusedBy(connection, "an assignment that has left its stage does not change again") {
                execute(connection, "UPDATE information_request_review_assignment SET draft_revision = 2 WHERE id = ?", assignment)
            }
        }
    }

    @Test
    fun `decisions are append-only, numbered, and held to the review's package, stage, and assignment`()
    {
        withReview { connection, review ->
            val reviewId = UUID.randomUUID()
            val assignment = UUID.randomUUID()
            review.insertReview(reviewId, 1, state = "IN_REVIEW")
            review.insertAssignment(assignment, reviewId)

            refusedBy(connection, "ck_information_request_review_decision_assignment") {
                review.insertDecision(UUID.randomUUID(), reviewId, 1)
            }
            refusedBy(connection, "ck_information_request_review_decision_narrative") {
                review.insertDecision(UUID.randomUUID(), reviewId, 1, kind = "OVERRIDE")
            }
            refusedBy(connection, "ck_information_request_review_decision_narrative") {
                review.insertDecision(UUID.randomUUID(), reviewId, 1, assignmentId = assignment, outcome = "WAIVED")
            }
            refusedBy(connection, "review decisions number from one without a gap") {
                review.insertDecision(UUID.randomUUID(), reviewId, 2, assignmentId = assignment)
            }
            refusedBy(connection, "a review decision names an item of its review's package") {
                review.insertDecision(
                    UUID.randomUUID(),
                    reviewId,
                    1,
                    assignmentId = assignment,
                    requirementId = review.runtime.attestationRequirementId,
                )
            }
            val decision = UUID.randomUUID()
            review.insertDecision(decision, reviewId, 1, assignmentId = assignment)
            refusedBy(connection, "ux_information_request_review_decision_reviewer") {
                review.insertDecision(UUID.randomUUID(), reviewId, 2, assignmentId = assignment)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_review_decision SET outcome = 'REJECTED' WHERE id = ?", decision)
            }
            review.insertDecision(UUID.randomUUID(), reviewId, 2, kind = "OVERRIDE", outcome = "SATISFIED_WITH_EXCEPTION", narrative = "Accepted as stated")
            settle(connection, reviewId, "SATISFIED_WITH_EXCEPTION")
            refusedBy(connection, "a settled review records no further decision") {
                review.insertDecision(UUID.randomUUID(), reviewId, 3, kind = "OVERRIDE", narrative = "Too late")
            }
        }
    }

    @Test
    fun `a finding names an item and only an evidence version its package froze for that item`()
    {
        withReview { connection, review ->
            val reviewId = UUID.randomUUID()
            review.insertReview(reviewId, 1, state = "IN_REVIEW")
            refusedBy(connection, "ck_information_request_review_finding_evidence") {
                review.insertFinding(UUID.randomUUID(), reviewId, 1, scope = "EVIDENCE_VERSION")
            }
            refusedBy(connection, "ck_information_request_review_finding_reason") {
                review.insertFinding(UUID.randomUUID(), reviewId, 1, reasonCode = "Not A Code")
            }
            refusedBy(connection, "a finding names an evidence version its package froze for the item") {
                review.insertFinding(
                    UUID.randomUUID(),
                    reviewId,
                    1,
                    evidenceVersionId = review.runtime.evidenceVersionId,
                    scope = "EVIDENCE_VERSION",
                    itemId = review.attestationItemId,
                    requirementId = review.runtime.attestationRequirementId,
                )
            }
            refusedBy(connection, "a finding retests a finding of the review its review follows") {
                review.insertFinding(UUID.randomUUID(), reviewId, 1, retests = UUID.randomUUID(), retestResult = "RESOLVED")
            }
            val finding = UUID.randomUUID()
            review.insertFinding(finding, reviewId, 1, evidenceVersionId = review.runtime.evidenceVersionId, scope = "EVIDENCE_VERSION")
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_review_finding SET severity = 'MINOR' WHERE id = ?", finding)
            }
        }
    }

    @Test
    fun `a correction follows a review that requested changes and allowlists only its package's items`()
    {
        withReview { connection, review ->
            val pendingReview = UUID.randomUUID()
            review.insertReview(pendingReview, 1, state = "IN_REVIEW")
            refusedBy(connection, "a correction follows a review that requested changes") {
                review.insertCorrection(UUID.randomUUID(), pendingReview)
            }
            settle(connection, pendingReview, "CHANGES_REQUESTED")
            val correction = UUID.randomUUID()
            review.insertCorrection(correction, pendingReview)

            val otherPackage = UUID.randomUUID()
            val otherItem = UUID.randomUUID()
            review.runtime.insertPackage(otherPackage, 2, previousPackageId = review.packageId, reviewRequired = true)
            review.runtime.insertItem(
                otherItem,
                otherPackage,
                review.runtime.documentRequirementId,
                review.runtime.documentRevisionId,
                review.runtime.documentBindingId,
                "DOCUMENT",
            )
            refusedBy(connection, "a correction allowlists an item of its package") {
                review.insertCorrectionItem(UUID.randomUUID(), correction, itemId = otherItem)
            }
            val attestationItem = UUID.randomUUID()
            review.insertCorrectionItem(
                attestationItem,
                correction,
                itemId = review.attestationItemId,
                requirementId = review.runtime.attestationRequirementId,
            )
            refusedBy(connection, "a correction returns only an evidence version its package froze for the item") {
                review.insertCorrectionEvidence(correction, attestationItem)
            }
            val documentItem = UUID.randomUUID()
            review.insertCorrectionItem(documentItem, correction)
            review.insertCorrectionEvidence(correction, documentItem)

            refusedBy(connection, "ck_information_request_correction_resubmission") {
                execute(connection, "UPDATE information_request_correction SET state = 'RESUBMITTED', closed_at = now() WHERE id = ?", correction)
            }
            execute(
                connection,
                "UPDATE information_request_correction SET state = 'RESUBMITTED', closed_at = now(), resubmitted_package_id = ? WHERE id = ?",
                otherPackage,
                correction,
            )
            refusedBy(connection, "a closed correction is immutable") {
                execute(connection, "UPDATE information_request_correction SET state = 'SUPERSEDED' WHERE id = ?", correction)
            }
        }
    }

    @Test
    fun `comments and remediation stay tied to one review item`()
    {
        withReview { connection, review ->
            val reviewId = UUID.randomUUID()
            review.insertReview(reviewId, 1, state = "IN_REVIEW")
            val finding = UUID.randomUUID()
            review.insertFinding(finding, reviewId, 1)
            refusedBy(connection, "ck_information_request_review_comment_visibility") {
                review.insertComment(UUID.randomUUID(), reviewId, 1, role = "RESPONDENT", visibility = "REVIEWERS_ONLY")
            }
            refusedBy(connection, "a comment answers a finding on its own item") {
                review.insertComment(
                    UUID.randomUUID(),
                    reviewId,
                    1,
                    findingId = finding,
                    itemId = review.attestationItemId,
                    requirementId = review.runtime.attestationRequirementId,
                )
            }
            val comment = UUID.randomUUID()
            review.insertComment(comment, reviewId, 1, findingId = finding)
            review.insertComment(UUID.randomUUID(), reviewId, 2, role = "RESPONDENT", findingId = finding, replyTo = comment)
            refusedBy(connection, "review comments number from one without a gap") {
                review.insertComment(UUID.randomUUID(), reviewId, 4)
            }

            settle(connection, reviewId, "CHANGES_REQUESTED")
            val correction = UUID.randomUUID()
            review.insertCorrection(correction, reviewId)
            val resubmitted = UUID.randomUUID()
            val resubmittedItem = UUID.randomUUID()
            review.runtime.insertPackage(resubmitted, 2, previousPackageId = review.packageId, reviewRequired = true)
            review.runtime.insertItem(
                resubmittedItem,
                resubmitted,
                review.runtime.documentRequirementId,
                review.runtime.documentRevisionId,
                review.runtime.documentBindingId,
                "DOCUMENT",
            )
            val foreignReview = UUID.randomUUID()
            review.insertReview(foreignReview, 2, kind = "RESUBMISSION", priorReviewId = reviewId, forPackage = resubmitted)
            val foreignFinding = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request_review_finding
                    (id, review_id, information_request_id, package_id, submission_item_id, requirement_id,
                     reason_code, narrative, severity, visibility, correction_scope, recorded_by_principal_kind,
                     recorded_by_principal_id, recorded_at, sequence_number)
                VALUES (?, ?, ?, ?, ?, ?, 'record.checked', 'Checked again', 'OBSERVATION', 'REVIEWERS_ONLY', 'NONE',
                        'USER', ?, now(), 1)
                """.trimIndent(),
                foreignFinding,
                foreignReview,
                review.runtime.requestId,
                resubmitted,
                resubmittedItem,
                review.runtime.documentRequirementId,
                review.reviewerUserId,
            )
            refusedBy(connection, "a remediation answers a finding of the corrected review") {
                insertRemediation(connection, review, correction, foreignFinding, resubmitted, resubmittedItem)
            }
            insertRemediation(connection, review, correction, finding, resubmitted, resubmittedItem)
            assertEquals(
                1,
                queryInt(connection, "SELECT COUNT(*) FROM information_request_review_remediation WHERE finding_id = ?", finding),
            )
        }
    }

    private fun withReview(block: (Connection, ReviewSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection, reviewed = true)
                block(connection, ReviewSqlFixture(connection, runtime))
            }
        }
    }

    private fun settle(connection: Connection, reviewId: UUID, state: String)
    {
        execute(
            connection,
            """
            UPDATE information_request_review
            SET state = ?, review_revision = review_revision + 1, settled_at = now(),
                settled_by_principal_kind = 'USER', settled_by_principal_id = opened_by_principal_id
            WHERE id = ?
            """.trimIndent(),
            state,
            reviewId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertRemediation(
        connection: Connection,
        review: ReviewSqlFixture,
        correctionId: UUID,
        findingId: UUID,
        packageId: UUID,
        itemId: UUID,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_review_remediation
                (id, information_request_id, correction_id, finding_id, remediated_by_package_id, remediated_by_item_id, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            review.runtime.requestId,
            correctionId,
            findingId,
            packageId,
            itemId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertTransition(
        connection: Connection,
        requestId: UUID,
        sequence: Int,
        from: String,
        to: String,
        mutation: String,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id)
            VALUES (?, ?, ?, ?, ?, ?, 'USER', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            sequence,
            from,
            to,
            mutation,
            UUID.randomUUID(),
        )
    }
}
