package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.*
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.noauth.contributorAsParticipant
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var decisionRepository: InformationRequestReviewDecisionRepository
    @Inject lateinit var correctionRepository: InformationRequestCorrectionRepository
    @Inject lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository
    @Inject lateinit var correctionEvidenceRepository: InformationRequestCorrectionEvidenceRepository
    @Inject lateinit var remediationRepository: InformationRequestReviewRemediationRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    @Test
    fun `a review-required package waits for review and a satisfied worksheet closes the request`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val packageId = submitWhole(services, fixture)

        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        assertEquals(InformationRequestReviewKind.INITIAL, review.kind)
        assertEquals(InformationRequestReviewState.PENDING, review.state)
        assertEquals(packageId, review.packageId)
        assertEquals(InformationRequestState.IN_PROGRESS, state(fixture))

        val assigned = assign(services, fixture, review)
        assertEquals(InformationRequestReviewState.IN_REVIEW, assigned.review.state)
        val drafted = draft(services, fixture, assigned, InformationRequestReviewOutcome.SATISFIED)
        val decisions = RecordInformationRequestReviewDecisionsCommand(
            requestId = fixture.requestId,
            reviewId = review.id,
            assignmentId = assigned.assignment!!.id,
            access = reviewer(fixture),
            precondition = CommandPrecondition.ExpectedRevision(drafted),
            idempotencyKey = "record-worksheet",
        )
        val settled = QuarkusTransaction.requiringNew().call { services.reviewDecisions.recordDecisions(decisions) }

        assertEquals(InformationRequestReviewState.SATISFIED, settled.review.state)
        assertEquals(InformationRequestState.CLOSED, settled.request.state)
        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            assertEquals(InformationRequestState.CLOSED, request.state)
            assertEquals(packageId, request.satisfiedByPackageId)
            val mutations = transitionRepository.findForRequest(fixture.requestId).map { it.mutation }
            assertTrue(
                mutations.containsAll(
                    listOf(
                        InformationRequestMutation.START_REVIEW,
                        InformationRequestMutation.ASSIGN_REVIEWER,
                        InformationRequestMutation.SAVE_REVIEW_DRAFT,
                        InformationRequestMutation.RECORD_REVIEW_DECISION,
                        InformationRequestMutation.SETTLE_REVIEW,
                        InformationRequestMutation.CLOSE,
                    ),
                ),
            )
        }

        val replayed = QuarkusTransaction.requiringNew().call { services.reviewDecisions.recordDecisions(decisions) }
        assertEquals(settled.review.id, replayed.review.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewDecisions.recordDecisions(decisions.copy(precondition = CommandPrecondition.ExpectedRevision("\"other\"")))
            }
        }
    }

    @Test
    fun `a returned item opens a correction that alone becomes editable and its resubmission is retested`()
    {
        val fixture = fixture(optionalRecord = true)
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        val finding = finding(services, fixture, review, documentItem(fixture, firstPackage), InformationRequestFindingCorrectionScope.RESPONSE)
        val drafted = draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED)
        val returned = record(services, fixture, assigned, drafted, "return-worksheet")

        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        assertEquals(InformationRequestState.IN_PROGRESS, returned.request.state)
        val correction = QuarkusTransaction.requiringNew().call { correctionRepository.findForRequest(fixture.requestId).single() }
        assertEquals(InformationRequestCorrectionState.OPEN, correction.state)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf(fixture.documentRequirementId),
                correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId },
            )
            assertTrue(correctionEvidenceRepository.findForCorrections(listOf(correction.id)).isEmpty())
        }

        val excluded = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { patchNarrative(services, fixture, fixture.optionalRequirementId, "Not returned") }
        }
        assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, excluded.reasonCode)
        val unreturnedFile = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.evidence.withdraw(
                    ChangeInformationRequestEvidenceStateCommand(
                        requestId = fixture.requestId,
                        requirementId = fixture.documentRequirementId,
                        artifactId = fixture.artifactId,
                        access = contributor(fixture),
                        precondition = CommandPrecondition.ExpectedRevision(com.docuhyphen.app.api.service.command.RevisionETag.of(fixture.artifactId, 1)),
                        idempotencyKey = "withdraw-unreturned",
                        reason = "Replacing the record",
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, unreturnedFile.reasonCode)
        QuarkusTransaction.requiringNew().run { patchNarrative(services, fixture, fixture.documentRequirementId, "The corrected note") }

        val secondPackage = submitWhole(services, fixture)
        QuarkusTransaction.requiringNew().run {
            val resubmitted = requireNotNull(correctionRepository.findById(correction.id))
            assertEquals(InformationRequestCorrectionState.RESUBMITTED, resubmitted.state)
            assertEquals(secondPackage, resubmitted.resubmittedPackageId)
            assertEquals(firstPackage, runtime.packageReader.view(fixture.requestId, secondPackage).submissionPackage.previousPackageId)
            assertEquals(listOf(finding.id), remediationRepository.findForRequest(fixture.requestId).map { it.findingId })
        }
        val retest = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).last() }
        assertEquals(InformationRequestReviewKind.RESUBMISSION, retest.kind)
        assertEquals(review.id, retest.priorReviewId)
        assertEquals(InformationRequestState.IN_PROGRESS, state(fixture))

        val reassigned = assign(services, fixture, retest)
        QuarkusTransaction.requiringNew().run {
            services.reviewFindings.record(
                RecordInformationRequestReviewFindingCommand(
                    requestId = fixture.requestId,
                    reviewId = retest.id,
                    submissionItemId = documentItem(fixture, secondPackage),
                    reasonCode = "record.checked",
                    narrative = "The note now states what was asked",
                    severity = InformationRequestFindingSeverity.OBSERVATION,
                    visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                    correctionScope = InformationRequestFindingCorrectionScope.NONE,
                    retestsFindingId = finding.id,
                    retestResult = InformationRequestRetestResult.RESOLVED,
                    access = reviewer(fixture),
                    idempotencyKey = "retest-finding",
                ),
            )
        }
        val accepted = record(services, fixture, reassigned, draft(services, fixture, reassigned, InformationRequestReviewOutcome.SATISFIED), "accept-retest")
        assertEquals(InformationRequestReviewState.SATISFIED, accepted.review.state)
        assertEquals(InformationRequestState.CLOSED, accepted.request.state)
    }

    @Test
    fun `an unchanged accepted item carries its outcome into the resubmission review`()
    {
        val fixture = fixture(beforePublish = { connection, runtime ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
                runtime.attestationBindingId,
            )
        })
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        finding(services, fixture, review, documentItem(fixture, firstPackage), InformationRequestFindingCorrectionScope.RESPONSE)
        val drafted = draft(
            services,
            fixture,
            assigned,
            outcomes = mapOf(
                documentItem(fixture, firstPackage) to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                attestationItem(fixture, firstPackage) to InformationRequestReviewOutcome.SATISFIED,
            ),
        )
        record(services, fixture, assigned, drafted, "partial-return")
        QuarkusTransaction.requiringNew().run { patchNarrative(services, fixture, fixture.documentRequirementId, "The corrected note") }

        val secondPackage = submitWhole(services, fixture)
        val retest = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).last() }
        QuarkusTransaction.requiringNew().run {
            val carried = decisionRepository.findForReview(retest.id)
            assertEquals(listOf(InformationRequestReviewDecisionKind.CARRIED), carried.map { it.kind })
            assertEquals(attestationItem(fixture, secondPackage), carried.single().submissionItemId)
            assertEquals(InformationRequestReviewOutcome.SATISFIED, carried.single().outcome)
            assertTrue(carried.single().carriedFromDecisionId != null)
        }
        val reassigned = assign(services, fixture, retest)
        val closing = record(
            services,
            fixture,
            reassigned,
            draft(services, fixture, reassigned, InformationRequestReviewOutcome.SATISFIED),
            "accept-changed-item",
        )
        assertEquals(InformationRequestReviewState.SATISFIED, closing.review.state)
        assertEquals(InformationRequestState.CLOSED, closing.request.state)
    }

    @Test
    fun `two correction cycles keep the package chain, review chain, remediations, and a carried decision traceable to the first review`() {
        val fixture = fixture(beforePublish = { connection, runtime ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
                runtime.attestationBindingId,
            )
        })
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        val first = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val firstAssigned = assign(services, fixture, first)
        val firstFinding = finding(
            services,
            fixture,
            first,
            documentItem(fixture, firstPackage),
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        record(
            services,
            fixture,
            firstAssigned,
            draft(
                services,
                fixture,
                firstAssigned,
                outcomes = mapOf(
                    documentItem(fixture, firstPackage) to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                    attestationItem(fixture, firstPackage) to InformationRequestReviewOutcome.SATISFIED,
                ),
            ),
            "first-return",
        )
        QuarkusTransaction.requiringNew()
            .run { patchNarrative(services, fixture, fixture.documentRequirementId, "The first corrected note") }

        val secondPackage = submitWhole(services, fixture)
        val second = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).last() }
        val secondAssigned = assign(services, fixture, second)
        val secondFinding = finding(
            services,
            fixture,
            second,
            documentItem(fixture, secondPackage),
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        record(
            services,
            fixture,
            secondAssigned,
            draft(services, fixture, secondAssigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "second-return"
        )
        QuarkusTransaction.requiringNew()
            .run { patchNarrative(services, fixture, fixture.documentRequirementId, "The second corrected note") }

        val thirdPackage = submitWhole(services, fixture)
        QuarkusTransaction.requiringNew().run {
            val reviews = reviewsOf(fixture)
            assertEquals(listOf(1, 2, 3), reviews.map { it.reviewNumber })
            assertEquals(
                listOf(
                    InformationRequestReviewKind.INITIAL,
                    InformationRequestReviewKind.RESUBMISSION,
                    InformationRequestReviewKind.RESUBMISSION
                ),
                reviews.map { it.kind },
            )
            assertEquals(listOf(null, first.id, second.id), reviews.map { it.priorReviewId })
            assertEquals(listOf(firstPackage, secondPackage, thirdPackage), reviews.map { it.packageId })

            assertEquals(
                secondPackage,
                runtime.packageReader.view(fixture.requestId, thirdPackage).submissionPackage.previousPackageId
            )
            assertEquals(
                firstPackage,
                runtime.packageReader.view(fixture.requestId, secondPackage).submissionPackage.previousPackageId
            )
            assertNull(runtime.packageReader.view(fixture.requestId, firstPackage).submissionPackage.previousPackageId)

            val corrections = correctionRepository.findForRequest(fixture.requestId)
            assertEquals(listOf(first.id, second.id), corrections.map { it.reviewId })
            assertEquals(listOf(firstPackage, secondPackage), corrections.map { it.packageId })
            assertEquals(
                listOf(InformationRequestCorrectionState.RESUBMITTED, InformationRequestCorrectionState.RESUBMITTED),
                corrections.map { it.state },
            )
            assertEquals(listOf(secondPackage, thirdPackage), corrections.map { it.resubmittedPackageId })

            val remediations = remediationRepository.findForRequest(fixture.requestId)
            assertEquals(
                listOf(firstFinding.id to secondPackage, secondFinding.id to thirdPackage),
                remediations.map { it.findingId to it.remediatedByPackageId },
            )
            assertEquals(corrections.map { it.id }, remediations.map { it.correctionId })
            assertEquals(
                listOf(documentItem(fixture, secondPackage), documentItem(fixture, thirdPackage)),
                remediations.map { it.remediatedByItemId },
            )

            val carried = decisionRepository.findForReview(reviews[2].id)
                .single { it.kind == InformationRequestReviewDecisionKind.CARRIED }
            assertEquals(attestationItem(fixture, thirdPackage), carried.submissionItemId)
            val throughSecond =
                requireNotNull(decisionRepository.findById(requireNotNull(carried.carriedFromDecisionId)))
            assertEquals(second.id, throughSecond.reviewId)
            assertEquals(InformationRequestReviewDecisionKind.CARRIED, throughSecond.kind)
            val origin =
                requireNotNull(decisionRepository.findById(requireNotNull(throughSecond.carriedFromDecisionId)))
            assertEquals(first.id, origin.reviewId)
            assertEquals(InformationRequestReviewDecisionKind.REVIEWER, origin.kind)
            assertEquals(firstAssigned.assignment!!.id, origin.assignmentId)
            assertEquals(attestationItem(fixture, firstPackage), origin.submissionItemId)
            assertEquals(InformationRequestReviewOutcome.SATISFIED, carried.outcome)
        }
    }

    @Test
    fun `a pending review withdraws with its package but an assigned one keeps the package from being withdrawn`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        QuarkusTransaction.requiringNew().run { services.submissions.withdraw(withdraw(fixture, firstPackage, "withdraw-first")) }
        QuarkusTransaction.requiringNew().run {
            assertEquals(InformationRequestReviewState.WITHDRAWN, reviewsOf(fixture).single().state)
        }

        val secondPackage = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).last() }
        assertEquals(InformationRequestReviewKind.INITIAL, review.kind)
        assign(services, fixture, review)
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { services.submissions.withdraw(withdraw(fixture, secondPackage, "withdraw-second")) }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE, refusal.reasonCode)
    }

    @Test
    fun `a correction resubmission cannot be withdrawn, so accepted items stay locked and the retest keeps its review chain`() {
        val fixture = fixture(optionalRecord = true)
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        finding(
            services,
            fixture,
            review,
            documentItem(fixture, firstPackage),
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        record(
            services,
            fixture,
            assigned,
            draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-worksheet"
        )
        QuarkusTransaction.requiringNew()
            .run { patchNarrative(services, fixture, fixture.documentRequirementId, "The corrected note") }
        val secondPackage = submitWhole(services, fixture)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.submissions.withdraw(withdraw(fixture, secondPackage, "withdraw-resubmission")) }
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE, refusal.reasonCode)
        val accepted = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { patchNarrative(services, fixture, fixture.optionalRequirementId, "Not returned") }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, accepted.reasonCode)
        QuarkusTransaction.requiringNew().run {
            val retest = reviewsOf(fixture).last()
            assertEquals(secondPackage, retest.packageId)
            assertEquals(InformationRequestReviewKind.RESUBMISSION, retest.kind)
            assertEquals(InformationRequestReviewState.PENDING, retest.state)
            assertEquals(review.id, retest.priorReviewId)
            assertEquals(
                firstPackage,
                runtime.packageReader.view(fixture.requestId, secondPackage).submissionPackage.previousPackageId
            )
        }
    }

    @Test
    fun `withdrawing a package a correction resubmission replaced says it was replaced`() {
        val fixture = fixture(optionalRecord = true)
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        finding(
            services,
            fixture,
            review,
            documentItem(fixture, firstPackage),
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        record(
            services,
            fixture,
            assigned,
            draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-replaced"
        )
        QuarkusTransaction.requiringNew()
            .run { patchNarrative(services, fixture, fixture.documentRequirementId, "The corrected note") }
        submitWhole(services, fixture)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.submissions.withdraw(withdraw(fixture, firstPackage, "withdraw-replaced")) }
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE, refusal.reasonCode)
        assertEquals("This Submission Package was replaced by a later submission", refusal.message)
        val standing = QuarkusTransaction.requiringNew().call {
            services.submissionQueries.packages(fixture.requestId, contributor(fixture))
                .associate { it.view.submissionPackage.id to (it.current to it.withdrawable) }
        }
        assertEquals(false to false, standing[firstPackage])
        assertEquals(true to false, standing.getValue(standing.keys.single { it != firstPackage }))
    }

    @Test
    fun `a package is offered for withdrawal only until a reviewer is assigned to its review`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val submitted = submitWhole(services, fixture)
        fun withdrawable() = QuarkusTransaction.requiringNew().call {
            services.submissionQueries.packages(fixture.requestId, contributor(fixture)).single().withdrawable
        }

        val beforeAssignment = withdrawable()

        assign(services, fixture, QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() })

        assertEquals(true, beforeAssignment)
        assertEquals(false, withdrawable())
        assertEquals(submitted, QuarkusTransaction.requiringNew().call {
            services.submissionQueries.packages(fixture.requestId, contributor(fixture))
                .single().view.submissionPackage.id
        })
    }

    @Test
    fun `a package submitted again after a withdrawal can itself be withdrawn while its review is pending`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val firstPackage = submitWhole(services, fixture)
        QuarkusTransaction.requiringNew()
            .run { services.submissions.withdraw(withdraw(fixture, firstPackage, "withdraw-first")) }
        val secondPackage = submitWhole(services, fixture)

        QuarkusTransaction.requiringNew()
            .run { services.submissions.withdraw(withdraw(fixture, secondPackage, "withdraw-second")) }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf(InformationRequestReviewState.WITHDRAWN, InformationRequestReviewState.WITHDRAWN),
                reviewsOf(fixture).map { it.state })
        }
    }

    @Test
    fun `a stage that excludes response parties refuses a reviewer who answered the submission`()
    {
        val conflictedParty = UUID.randomUUID()
        val fixture = fixture(
            beforePublish = { connection, runtime ->
                execute(
                    connection,
                    """
                    INSERT INTO information_request_template_review_stage
                        (id, template_version_id, stage_key, position, title, aggregation, minimum_reviewer_count,
                         tie_resolution, override_permitted, excludes_response_parties, excludes_prior_reviewers)
                    VALUES (gen_random_uuid(), ?, 'independent-check', 1, 'Independent check', 'ANY', 1,
                            'MOST_SEVERE_OUTCOME', FALSE, TRUE, TRUE)
                    """.trimIndent(),
                    runtime.template.versionId,
                )
            },
            extraParties = { runtime -> runtime.insertPartyForUser(conflictedParty, "REVIEWER", runtime.contributorUserId) },
        )
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewAssignments.assign(assignment(fixture, review, conflictedParty, "independent-check", "conflicted"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
        val independent = QuarkusTransaction.requiringNew().call {
            services.reviewAssignments.assign(assignment(fixture, review, fixture.reviewerPartyId, "independent-check", "independent"))
        }
        assertEquals("independent-check", independent.assignment!!.stageKey)
    }

    @Test
    fun `a reviewer whose account is linked to the submitting participant cannot decide once that participant's party is reassigned`() {
        val linked = linkedAccountJourney()
        val assigned = QuarkusTransaction.requiringNew().call {
            linked.services.reviewAssignments.assign(
                assignment(
                    linked.fixture,
                    linked.review,
                    linked.reviewerPartyId,
                    "independent-check",
                    "linked-reviewer"
                ),
            )
        }
        linked.linkParticipantAccount()
        linked.reassignRespondentParty()
        val draftETag =
            draft(linked.services, linked.fixture, assigned, InformationRequestReviewOutcome.SATISFIED, linked.access)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            record(linked.services, linked.fixture, assigned, draftETag, "linked-reviewer-decision", linked.access)
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
        assertTrue(QuarkusTransaction.requiringNew().call { decisionRepository.findForReview(linked.review.id) }
            .isEmpty())
    }

    @Test
    fun `a reviewer whose account is linked to the submitting participant cannot be assigned after that participant's party is reassigned`() {
        val linked = linkedAccountJourney()
        linked.linkParticipantAccount()
        linked.reassignRespondentParty()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                linked.services.reviewAssignments.assign(
                    assignment(
                        linked.fixture,
                        linked.review,
                        linked.reviewerPartyId,
                        "independent-check",
                        "linked-reviewer"
                    ),
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
        val independent = QuarkusTransaction.requiringNew().call {
            linked.services.reviewAssignments.assign(
                assignment(
                    linked.fixture,
                    linked.review,
                    linked.fixture.reviewerPartyId,
                    "independent-check",
                    "independent"
                ),
            )
        }
        assertEquals("independent-check", independent.assignment!!.stageKey)
    }

    private fun linkedAccountJourney(): LinkedAccountJourney {
        val reviewerPartyId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val fixture = fixture(
            beforePublish = { connection, runtime ->
                execute(
                    connection,
                    """
                    INSERT INTO information_request_template_review_stage
                        (id, template_version_id, stage_key, position, title, aggregation, minimum_reviewer_count,
                         tie_resolution, override_permitted, excludes_response_parties, excludes_prior_reviewers)
                    VALUES (gen_random_uuid(), ?, 'independent-check', 1, 'Independent check', 'ANY', 1,
                            'MOST_SEVERE_OUTCOME', FALSE, TRUE, FALSE)
                    """.trimIndent(),
                    runtime.template.versionId,
                )
            },
            extraParties = { runtime -> runtime.insertActingParty(reviewerPartyId, "REVIEWER", accountId) },
        )
        val participantId = dataSource.connection.use { contributorAsParticipant(it, fixture.runtime) }
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture, participant(participantId))
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        assertEquals(participantId, packageSubmitter(fixture))
        return LinkedAccountJourney(fixture, services, review, reviewerPartyId, accountId, participantId)
    }

    private fun packageSubmitter(fixture: ReviewFixture): UUID? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT submitted_by_principal_id FROM information_request_submission_package WHERE information_request_id = ?",
        ).use { statement ->
            statement.setObject(1, fixture.requestId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getObject(1, UUID::class.java) else null }
        }
    }

    private inner class LinkedAccountJourney(
        val fixture: ReviewFixture,
        val services: InformationRequestRuntimeServices,
        val review: InformationRequestReview,
        val reviewerPartyId: UUID,
        private val accountId: UUID,
        private val participantId: UUID,
    ) {
        val access =
            RequestAccessContext(PrincipalRef.user(accountId), AuthorizationContext(sessionRef = "linked-session"))

        fun linkParticipantAccount() {
            dataSource.connection.use { connection ->
                val linkId = UUID.randomUUID()
                execute(
                    connection,
                    """
                    INSERT INTO share_link
                        (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                         contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
                    SELECT ?, share_id, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 1, FALSE, 0, 0, 0, 5,
                           now() + interval '30 days', now(), 'EMAIL_OTP'
                    FROM information_request_party WHERE id = ?
                    """.trimIndent(),
                    linkId,
                    "linked-account-link-$linkId",
                    fixture.runtime.contributorPartyId,
                )
                execute(
                    connection,
                    """
                    INSERT INTO participant_account_link
                        (id, participant_id, app_user_id, linked_via_information_request_id, linked_via_share_link_id, linked_at)
                    VALUES (?, ?, ?, ?, ?, now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    participantId,
                    accountId,
                    fixture.requestId,
                    linkId,
                )
            }
        }

        fun reassignRespondentParty() {
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    """
                    UPDATE information_request_party
                    SET principal_kind = 'USER', principal_id = ?, party_revision = party_revision + 1
                    WHERE id = ?
                    """.trimIndent(),
                    fixture.runtime.contributorUserId,
                    fixture.runtime.contributorPartyId,
                )
            }
        }
    }

    @Test
    fun `a rejected review is reconsidered and a returned review appealed, each naming the exact prior review`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val packageId = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        finding(services, fixture, review, documentItem(fixture, packageId), InformationRequestFindingCorrectionScope.NONE)
        val rejected = record(services, fixture, assigned, draft(services, fixture, assigned, InformationRequestReviewOutcome.REJECTED), "reject")
        assertEquals(InformationRequestReviewState.REJECTED, rejected.review.state)
        assertEquals(InformationRequestState.IN_PROGRESS, rejected.request.state)

        val reconsidered = QuarkusTransaction.requiringNew().call {
            services.reviewCycles.reopen(reopen(fixture, rejected, InformationRequestReviewKind.RECONSIDERATION, owner(fixture), "reconsider"))
        }
        assertEquals(InformationRequestReviewKind.RECONSIDERATION, reconsidered.review.kind)
        assertEquals(review.id, reconsidered.review.priorReviewId)

        val secondAssignment = assign(services, fixture, reconsidered.review)
        finding(services, fixture, reconsidered.review, documentItem(fixture, packageId), InformationRequestFindingCorrectionScope.RESPONSE)
        val returned = record(
            services,
            fixture,
            secondAssignment,
            draft(services, fixture, secondAssignment, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-after-reconsideration",
        )
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)

        val stale = assertThrows(CommandPreconditionException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewCycles.reopen(
                    reopen(fixture, returned, InformationRequestReviewKind.APPEAL, contributor(fixture), "stale-appeal")
                        .copy(precondition = CommandPrecondition.ExpectedRevision("\"stale\"")),
                )
            }
        }
        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        val appealed = QuarkusTransaction.requiringNew().call {
            services.reviewCycles.reopen(reopen(fixture, returned, InformationRequestReviewKind.APPEAL, contributor(fixture), "appeal"))
        }
        assertEquals(InformationRequestReviewKind.APPEAL, appealed.review.kind)
        assertEquals(returned.review.id, appealed.review.priorReviewId)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestCorrectionState.SUPERSEDED,
                correctionRepository.findForRequest(fixture.requestId).single().state,
            )
        }
    }

    @Test
    fun `an appeal sent again under its key returns the recorded appeal, and the same appeal under another key is refused`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val packageId = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        finding(
            services,
            fixture,
            review,
            documentItem(fixture, packageId),
            InformationRequestFindingCorrectionScope.NONE
        )
        val rejected = record(
            services,
            fixture,
            assigned,
            draft(services, fixture, assigned, InformationRequestReviewOutcome.REJECTED),
            "reject"
        )
        val appeal = reopen(fixture, rejected, InformationRequestReviewKind.APPEAL, contributor(fixture), "appeal-once")

        val appealed = QuarkusTransaction.requiringNew().call { services.reviewCycles.reopen(appeal) }
        val replayed = QuarkusTransaction.requiringNew().call { services.reviewCycles.reopen(appeal) }

        assertEquals(appealed.review.id, replayed.review.id)
        assertEquals(InformationRequestReviewKind.APPEAL, replayed.review.kind)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewCycles.reopen(appeal.copy(precondition = CommandPrecondition.ExpectedRevision("\"review:another-revision\"")))
            }
        }
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewCycles.reopen(appeal.copy(idempotencyKey = "appeal-under-another-key")) }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_NOT_REOPENABLE, refusal.reasonCode)
        assertEquals(
            listOf(review.id, appealed.review.id),
            QuarkusTransaction.requiringNew().call { reviewsOf(fixture).map { it.id } })
    }

    @Test
    fun `an assignment sent again under its key replays only with the If-Match it was made with`() {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val command = assignment(fixture, review, fixture.reviewerPartyId, "review", "assign-once")

        val assigned = QuarkusTransaction.requiringNew().call { services.reviewAssignments.assign(command) }
        val replayed = QuarkusTransaction.requiringNew().call { services.reviewAssignments.assign(command) }

        assertEquals(assigned.assignment?.id, replayed.assignment?.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewAssignments.assign(
                    command.copy(
                        precondition = CommandPrecondition.ExpectedRevision(
                            reviewETag(review.id)
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `a worksheet needs a current precondition and a second save against the same revision is stale`()
    {
        val fixture = fixture()
        val services = runtime.build(fixture.requestId)
        val packageId = submitWhole(services, fixture)
        val review = QuarkusTransaction.requiringNew().call { reviewsOf(fixture).single() }
        val assigned = assign(services, fixture, review)
        val item = documentItem(fixture, packageId)
        fun save(precondition: CommandPrecondition) = QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.saveDraft(
                SaveInformationRequestReviewDraftCommand(
                    requestId = fixture.requestId,
                    reviewId = review.id,
                    assignmentId = assigned.assignment!!.id,
                    patches = listOf(InformationRequestReviewDraftPatch(item, InformationRequestReviewOutcome.SATISFIED)),
                    access = reviewer(fixture),
                    precondition = precondition,
                ),
            )
        }

        val missing = assertThrows(CommandPreconditionException::class.java) { save(CommandPrecondition.Absent) }
        assertEquals(CommandPreconditionException.Kind.REQUIRED, missing.kind)
        val first = save(CommandPrecondition.ExpectedRevision(assigned.draftETag!!))
        assertTrue(first.draftETag != assigned.draftETag)
        val second = assertThrows(CommandPreconditionException::class.java) {
            save(CommandPrecondition.ExpectedRevision(assigned.draftETag!!))
        }
        assertEquals(CommandPreconditionException.Kind.STALE, second.kind)
    }

    private fun fixture(
        optionalRecord: Boolean = false,
        beforePublish: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { _, _ -> },
        extraParties: (SubmissionRuntimeSqlFixture) -> Unit = {},
    ): ReviewFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(
                connection,
                reviewed = true,
                optionalRecord = optionalRecord,
                beforePublish = { runtime -> beforePublish(connection, runtime) },
            )
            val reviewerPartyId = UUID.randomUUID()
            val reviewerUserId = UUID.randomUUID()
            runtimeFixture.insertActingParty(reviewerPartyId, "REVIEWER", reviewerUserId)
            extraParties(runtimeFixture)
            ReviewFixture(runtimeFixture, reviewerPartyId, reviewerUserId)
        }

    private fun reviewsOf(fixture: ReviewFixture): List<InformationRequestReview> = reviewRepository.findForRequest(fixture.requestId)

    private fun state(fixture: ReviewFixture): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(fixture.requestId)).state }

    private fun documentItem(fixture: ReviewFixture, packageId: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            runtime.packageReader.view(fixture.requestId, packageId).items
                .single { it.informationRequestRequirementId == fixture.documentRequirementId }.id
        }

    private fun attestationItem(fixture: ReviewFixture, packageId: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            runtime.packageReader.view(fixture.requestId, packageId).items
                .single { it.informationRequestRequirementId == fixture.attestationRequirementId }.id
        }

    private fun submitWhole(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        submitter: RequestAccessContext = contributor(fixture),
    ): UUID
    {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = attestor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture)),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = submitter,
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id
    }

    private fun submissionETag(fixture: ReviewFixture): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun responseETag(fixture: ReviewFixture): String =
        QuarkusTransaction.requiringNew().call { InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))) }

    private fun reviewETag(reviewId: UUID): String =
        QuarkusTransaction.requiringNew().call { runtime.reviewLoader.reviewETag(requireNotNull(reviewRepository.findById(reviewId))) }

    private fun patchNarrative(services: InformationRequestRuntimeServices, fixture: ReviewFixture, requirementId: UUID, narrative: String) =
        services.responses.patch(
            PatchInformationRequestResponsesCommand(
                requestId = fixture.requestId,
                access = contributor(fixture),
                precondition = CommandPrecondition.ExpectedRevision(
                    InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))),
                ),
                idempotencyKey = "patch-${UUID.randomUUID()}",
                patches = listOf(
                    InformationRequestResponsePatch(requirementId = requirementId, narrative = ResponseNarrativePatch.Set(narrative)),
                ),
            ),
        )

    private fun assign(services: InformationRequestRuntimeServices, fixture: ReviewFixture, review: InformationRequestReview) =
        QuarkusTransaction.requiringNew().call {
            services.reviewAssignments.assign(assignment(fixture, review, fixture.reviewerPartyId, "review", "assign-${UUID.randomUUID()}"))
        }

    private fun assignment(fixture: ReviewFixture, review: InformationRequestReview, partyId: UUID, stageKey: String, key: String) =
        AssignInformationRequestReviewerCommand(
            requestId = fixture.requestId,
            reviewId = review.id,
            stageKey = stageKey,
            reviewerPartyId = partyId,
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(reviewETag(review.id)),
            idempotencyKey = key,
        )

    private fun draft(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        assigned: InformationRequestReviewCommandResult,
        outcome: InformationRequestReviewOutcome? = null,
        access: RequestAccessContext = reviewer(fixture),
        outcomes: Map<UUID, InformationRequestReviewOutcome>? = null,
    ): String
    {
        val entries = outcomes ?: QuarkusTransaction.requiringNew().call {
            val snapshot = runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(assigned.review.id)))
            snapshot.coverage[assigned.assignment!!.stageKey].orEmpty()
                .filterNot { id -> snapshot.decisions.any { it.submissionItemId == id && it.kind == InformationRequestReviewDecisionKind.CARRIED } }
                .associateWith { requireNotNull(outcome) }
        }
        return QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.saveDraft(
                SaveInformationRequestReviewDraftCommand(
                    requestId = fixture.requestId,
                    reviewId = assigned.review.id,
                    assignmentId = assigned.assignment!!.id,
                    patches = entries.map { (itemId, value) ->
                        InformationRequestReviewDraftPatch(itemId, value, narrative = "Checked against the request")
                    },
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(requireNotNull(assigned.draftETag)),
                ),
            )
        }.draftETag
    }

    private fun record(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        assigned: InformationRequestReviewCommandResult,
        draftETag: String,
        key: String,
        access: RequestAccessContext = reviewer(fixture),
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewDecisions.recordDecisions(
            RecordInformationRequestReviewDecisionsCommand(
                requestId = fixture.requestId,
                reviewId = assigned.review.id,
                assignmentId = assigned.assignment!!.id,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(draftETag),
                idempotencyKey = key,
            ),
        )
    }

    private fun finding(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        review: InformationRequestReview,
        itemId: UUID,
        scope: InformationRequestFindingCorrectionScope,
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewFindings.record(
            RecordInformationRequestReviewFindingCommand(
                requestId = fixture.requestId,
                reviewId = review.id,
                submissionItemId = itemId,
                reasonCode = "record.incomplete",
                narrative = "The note does not say what was asked",
                severity = InformationRequestFindingSeverity.MAJOR,
                visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                correctionScope = scope,
                access = reviewer(fixture),
                idempotencyKey = "finding-${UUID.randomUUID()}",
            ),
        )
    }.finding!!

    private fun reopen(
        fixture: ReviewFixture,
        prior: InformationRequestReviewCommandResult,
        kind: InformationRequestReviewKind,
        access: RequestAccessContext,
        key: String,
    ) = ReopenInformationRequestReviewCommand(
        requestId = fixture.requestId,
        reviewId = prior.review.id,
        kind = kind,
        reason = "The recorded item was complete",
        access = access,
        precondition = CommandPrecondition.ExpectedRevision(reviewETag(prior.review.id)),
        idempotencyKey = key,
    )

    private fun withdraw(fixture: ReviewFixture, packageId: UUID, key: String) =
        WithdrawInformationRequestPackageCommand(
            requestId = fixture.requestId,
            packageId = packageId,
            access = contributor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(responseETag(fixture)),
            idempotencyKey = key,
        )

    private fun owner(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    private fun attestor(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.attestorUserId), AuthorizationContext(sessionRef = "attestor-session"))

    private fun participant(participantId: UUID) =
        RequestAccessContext(
            PrincipalRef.participant(participantId),
            AuthorizationContext(sessionRef = "participant-session")
        )

    private fun reviewer(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.reviewerUserId), AuthorizationContext(sessionRef = "reviewer-session"))
}
