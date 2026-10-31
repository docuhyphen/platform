package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewDecisionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var decisionRepository: InformationRequestReviewDecisionRepository

    @Inject
    lateinit var correctionRepository: InformationRequestCorrectionRepository

    @Inject
    lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a worksheet reviews only its stage's items in full and each outcome carries the finding or reason it needs`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val attestationItem = support.item(fixture, packageId, fixture.attestationRequirementId)
        val worksheet = Worksheet(services, fixture, assigned, support.reviewer(fixture))

        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED,
            refusal {
                worksheet.save(
                    InformationRequestReviewDraftPatch(
                        attestationItem,
                        InformationRequestReviewOutcome.SATISFIED
                    )
                )
            },
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_WORKSHEET_INCOMPLETE,
            refusal { worksheet.record("incomplete") })

        worksheet.save(
            InformationRequestReviewDraftPatch(
                documentItem,
                InformationRequestReviewOutcome.CHANGES_REQUIRED
            )
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_FINDING_REQUIRED,
            refusal { worksheet.record("returned-without-finding") })
        support.finding(
            services,
            fixture,
            review,
            documentItem,
            InformationRequestFindingCorrectionScope.RESPONSE,
            reviewerAccess = support.owner(fixture)
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_FINDING_REQUIRED,
            refusal { worksheet.record("returned-with-administrator-finding") })

        worksheet.save(
            InformationRequestReviewDraftPatch(
                documentItem,
                InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION
            )
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_NARRATIVE_REQUIRED,
            refusal { worksheet.record("exception-without-reason") })
        worksheet.save(
            InformationRequestReviewDraftPatch(
                documentItem,
                InformationRequestReviewOutcome.WAIVED,
                narrative = "The record is not needed"
            )
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_OUTCOME_NOT_PERMITTED,
            refusal { worksheet.record("waived-without-waiver") })

        support.finding(services, fixture, review, documentItem, InformationRequestFindingCorrectionScope.RESPONSE)
        worksheet.save(
            InformationRequestReviewDraftPatch(
                documentItem,
                InformationRequestReviewOutcome.CHANGES_REQUIRED
            )
        )
        val returned = worksheet.record("returned")
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        assertEquals(InformationRequestErrorCatalog.REVIEW_SETTLED, refusal { worksheet.record("returned-again") })
    }

    @Test
    fun `every reviewer of an all-reviewers stage decides before it settles on the most severe outcome`() {
        val colleagueParty = UUID.randomUUID()
        val colleagueUser = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, runtime ->
                insertReviewStage(
                    connection,
                    runtime.template.versionId,
                    JOINT_CHECK,
                    1,
                    aggregation = "ALL",
                    minimumReviewers = 2
                )
            },
            prepare = { runtime -> runtime.insertActingParty(colleagueParty, "REVIEWER", colleagueUser) },
        )
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueUser))
        )
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val reviewer = support.reviewer(fixture)
        val colleague = support.access(colleagueUser)
        val first =
            Worksheet(services, fixture, support.assign(services, fixture, review, stageKey = JOINT_CHECK), reviewer)
        val second = Worksheet(
            services,
            fixture,
            support.assign(services, fixture, review, partyId = colleagueParty, stageKey = JOINT_CHECK),
            colleague
        )

        first.save(InformationRequestReviewDraftPatch(documentItem, InformationRequestReviewOutcome.SATISFIED))
        val halfway = first.record("first-accepts")
        assertEquals(InformationRequestReviewState.IN_REVIEW, halfway.review.state)
        assertEquals(InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED, refusal { first.record("first-again") })
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED,
            refusal {
                first.save(
                    InformationRequestReviewDraftPatch(
                        documentItem,
                        InformationRequestReviewOutcome.REJECTED
                    )
                )
            },
        )

        support.finding(
            services,
            fixture,
            review,
            documentItem,
            InformationRequestFindingCorrectionScope.RESPONSE,
            reviewerAccess = colleague
        )
        second.save(InformationRequestReviewDraftPatch(documentItem, InformationRequestReviewOutcome.CHANGES_REQUIRED))
        val settled = second.record("second-returns")
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, settled.review.state)
        assertEquals(InformationRequestState.IN_PROGRESS, settled.request.state)
        QuarkusTransaction.requiringNew().run {
            val snapshot = runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(review.id)))
            assertEquals(InformationRequestReviewOutcome.CHANGES_REQUIRED, snapshot.standing.itemOutcomes[documentItem])
            val correction = correctionRepository.findForRequest(fixture.requestId).single()
            assertEquals(InformationRequestCorrectionState.OPEN, correction.state)
            assertEquals(
                listOf(fixture.documentRequirementId),
                correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId },
            )
        }
    }

    @Test
    fun `assigning the reviewer who staffs an any-reviewer stage that already holds a decision settles the review at once`() {
        val colleagueParty = UUID.randomUUID()
        val colleagueUser = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, runtime ->
                insertReviewStage(
                    connection,
                    runtime.template.versionId,
                    JOINT_CHECK,
                    1,
                    aggregation = "ANY",
                    minimumReviewers = 2
                )
            },
            prepare = { runtime -> runtime.insertActingParty(colleagueParty, "REVIEWER", colleagueUser) },
        )
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueUser))
        )
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val first =
            Worksheet(
                services,
                fixture,
                support.assign(services, fixture, review, stageKey = JOINT_CHECK),
                support.reviewer(fixture)
            )
        first.save(InformationRequestReviewDraftPatch(documentItem, InformationRequestReviewOutcome.SATISFIED))
        assertEquals(InformationRequestReviewState.IN_REVIEW, first.record("first-accepts-alone").review.state)

        val staffed = support.assign(services, fixture, review, partyId = colleagueParty, stageKey = JOINT_CHECK)

        assertEquals(InformationRequestReviewState.SATISFIED, staffed.review.state)
        assertEquals(InformationRequestState.CLOSED, support.state(fixture))
    }

    @Test
    fun `a consensus tie waits for an authorized override, which only an administrator records with its reason`() {
        val colleagueParty = UUID.randomUUID()
        val colleagueUser = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, runtime ->
                reviewAttestation(connection, runtime)
                insertReviewStage(
                    connection,
                    runtime.template.versionId,
                    CONSENSUS_CHECK,
                    1,
                    aggregation = "CONSENSUS",
                    minimumReviewers = 2,
                    tieResolution = "REQUIRE_OVERRIDE",
                    overridePermitted = true,
                )
            },
            prepare = { runtime -> runtime.insertActingParty(colleagueParty, "REVIEWER", colleagueUser) },
        )
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueUser))
        )
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val attestationItem = support.item(fixture, packageId, fixture.attestationRequirementId)
        val first = Worksheet(
            services,
            fixture,
            support.assign(services, fixture, review, stageKey = CONSENSUS_CHECK),
            support.reviewer(fixture)
        )
        val second = Worksheet(
            services,
            fixture,
            support.assign(services, fixture, review, partyId = colleagueParty, stageKey = CONSENSUS_CHECK),
            support.access(colleagueUser),
        )
        first.save(
            InformationRequestReviewDraftPatch(documentItem, InformationRequestReviewOutcome.SATISFIED),
            InformationRequestReviewDraftPatch(attestationItem, InformationRequestReviewOutcome.SATISFIED),
        )
        first.record("first-decides")
        second.save(
            InformationRequestReviewDraftPatch(
                documentItem,
                InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION,
                narrative = "The note is late"
            ),
            InformationRequestReviewDraftPatch(attestationItem, InformationRequestReviewOutcome.SATISFIED),
        )
        val tied = second.record("second-decides")
        assertEquals(InformationRequestReviewState.IN_REVIEW, tied.review.state)
        QuarkusTransaction.requiringNew().run {
            val standing =
                runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(review.id))).standing.stage(
                    CONSENSUS_CHECK
                )!!
            assertEquals(
                InformationRequestReviewItemStanding.TIED,
                standing.items.single { it.itemId == documentItem }.standing
            )
            assertEquals(
                InformationRequestReviewItemStanding.DECIDED,
                standing.items.single { it.itemId == attestationItem }.standing
            )
        }

        val owner = support.owner(fixture)
        fun overrideCommand(
            itemId: UUID,
            access: RequestAccessContext = owner,
            stageKey: String = CONSENSUS_CHECK,
            narrative: String = "The exception is accepted",
            key: String = "override-${UUID.randomUUID()}",
        ) =
            OverrideInformationRequestReviewItemCommand(
                requestId = fixture.requestId,
                reviewId = review.id,
                stageKey = stageKey,
                submissionItemId = itemId,
                outcome = InformationRequestReviewOutcome.SATISFIED,
                narrative = narrative,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(review.id)),
                idempotencyKey = key,
            )

        fun applyOverride(command: OverrideInformationRequestReviewItemCommand) =
            QuarkusTransaction.requiringNew().call { services.reviewDecisions.override(command) }

        assertThrows(ForbiddenException::class.java) {
            applyOverride(
                overrideCommand(
                    documentItem,
                    access = support.reviewer(fixture)
                )
            )
        }
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_STAGE_UNKNOWN,
            refusal { applyOverride(overrideCommand(documentItem, stageKey = "missing-stage")) })
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED,
            refusal { applyOverride(overrideCommand(UUID.randomUUID())) })
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED,
            refusal { applyOverride(overrideCommand(attestationItem)) })
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_NARRATIVE_REQUIRED,
            refusal { applyOverride(overrideCommand(documentItem, narrative = " ")) })

        val command = overrideCommand(documentItem, key = "override-tie")
        val overridden = applyOverride(command)
        assertEquals(InformationRequestReviewState.SATISFIED, overridden.review.state)
        assertEquals(InformationRequestState.CLOSED, overridden.request.state)
        assertEquals(overridden.review.id, applyOverride(command).review.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            applyOverride(command.copy(precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(review.id))))
        }
        QuarkusTransaction.requiringNew().run {
            val decision = decisionRepository.findForReview(review.id)
                .single { it.kind == InformationRequestReviewDecisionKind.OVERRIDE }
            assertEquals(documentItem, decision.submissionItemId)
            assertEquals("The exception is accepted", decision.narrative)
            assertEquals(owner.principal.id, decision.decidedByPrincipalId)
        }
    }

    @Test
    fun `a later sequential stage waits for the earlier one and a stage without override refuses one`() {
        val colleagueParty = UUID.randomUUID()
        val colleagueUser = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, runtime ->
                insertReviewStage(connection, runtime.template.versionId, FIRST_CHECK, 1)
                insertReviewStage(connection, runtime.template.versionId, SECOND_CHECK, 2)
            },
            prepare = { runtime -> runtime.insertActingParty(colleagueParty, "REVIEWER", colleagueUser) },
        )
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueUser))
        )
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        support.assign(services, fixture, review, stageKey = FIRST_CHECK)
        val later = Worksheet(
            services,
            fixture,
            support.assign(services, fixture, review, partyId = colleagueParty, stageKey = SECOND_CHECK),
            support.access(colleagueUser),
        )

        assertEquals(
            InformationRequestErrorCatalog.REVIEW_STAGE_NOT_OPEN,
            refusal {
                later.save(
                    InformationRequestReviewDraftPatch(
                        documentItem,
                        InformationRequestReviewOutcome.SATISFIED
                    )
                )
            },
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_OVERRIDE_NOT_PERMITTED,
            refusal {
                QuarkusTransaction.requiringNew().call {
                    services.reviewDecisions.override(
                        OverrideInformationRequestReviewItemCommand(
                            requestId = fixture.requestId,
                            reviewId = review.id,
                            stageKey = FIRST_CHECK,
                            submissionItemId = documentItem,
                            outcome = InformationRequestReviewOutcome.SATISFIED,
                            narrative = "Accepted by the administrator",
                            access = support.owner(fixture),
                            precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(review.id)),
                            idempotencyKey = "override-not-permitted",
                        ),
                    )
                }
            },
        )
    }

    private fun refusal(block: () -> Any?): String =
        assertThrows(InformationRequestLifecycleException::class.java) { block() }.reasonCode

    private class Worksheet(
        private val services: InformationRequestRuntimeServices,
        private val fixture: ReviewFixture,
        private val assigned: InformationRequestReviewCommandResult,
        private val access: RequestAccessContext,
    ) {
        private var draftETag: String = requireNotNull(assigned.draftETag)
        private val review: InformationRequestReview get() = assigned.review

        fun save(vararg patches: InformationRequestReviewDraftPatch) {
            draftETag = QuarkusTransaction.requiringNew().call {
                services.reviewDecisions.saveDraft(
                    SaveInformationRequestReviewDraftCommand(
                        requestId = fixture.requestId,
                        reviewId = review.id,
                        assignmentId = assigned.assignment!!.id,
                        patches = patches.toList(),
                        access = access,
                        precondition = CommandPrecondition.ExpectedRevision(draftETag),
                    ),
                )
            }.draftETag
        }

        fun record(key: String): InformationRequestReviewCommandResult =
            QuarkusTransaction.requiringNew().call {
                services.reviewDecisions.recordDecisions(
                    RecordInformationRequestReviewDecisionsCommand(
                        requestId = fixture.requestId,
                        reviewId = review.id,
                        assignmentId = assigned.assignment!!.id,
                        access = access,
                        precondition = CommandPrecondition.ExpectedRevision(draftETag),
                        idempotencyKey = key,
                    ),
                )
            }
    }

    private companion object {
        const val JOINT_CHECK = "joint-check"
        const val CONSENSUS_CHECK = "consensus-check"
        const val FIRST_CHECK = "first-check"
        const val SECOND_CHECK = "second-check"
    }
}
