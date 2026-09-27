package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestRetestResult
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.AssignInformationRequestReviewerCommand
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewDraftPatch
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestReviewDecisionsCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestReviewFindingCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.ReopenInformationRequestReviewCommand
import com.docuhyphen.app.api.model.informationrequest.SaveInformationRequestReviewDraftCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRemediationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID
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

    private fun submitWhole(services: InformationRequestRuntimeServices, fixture: ReviewFixture): UUID
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
                    access = contributor(fixture),
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
                    access = reviewer(fixture),
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
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewDecisions.recordDecisions(
            RecordInformationRequestReviewDecisionsCommand(
                requestId = fixture.requestId,
                reviewId = assigned.review.id,
                assignmentId = assigned.assignment!!.id,
                access = reviewer(fixture),
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

    private fun reviewer(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.reviewerUserId), AuthorizationContext(sessionRef = "reviewer-session"))

    internal data class ReviewFixture(
        val runtime: SubmissionRuntimeSqlFixture,
        val reviewerPartyId: UUID,
        val reviewerUserId: UUID,
    )
    {
        val requestId: UUID get() = runtime.requestId
        val documentRequirementId: UUID get() = runtime.documentRequirementId
        val attestationRequirementId: UUID get() = runtime.attestationRequirementId
        val optionalRequirementId: UUID get() = runtime.optionalRequirementId
        val artifactId: UUID get() = runtime.artifactId
    }
}
