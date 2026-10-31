package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewAssignmentChange
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewFindingCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewCommentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewFindingTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var findingRepository: InformationRequestReviewFindingRepository

    @Inject
    lateinit var commentRepository: InformationRequestReviewCommentRepository

    @Inject
    lateinit var transitionRepository: InformationRequestTransitionRepository

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a reviewer holding two open assignments names the one a finding belongs to and its worksheet records against it`() {
        val fixture = twoStageFixture("PARALLEL")
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review, stageKey = FIRST_CHECK)
        val second = support.assign(services, fixture, review, stageKey = SECOND_CHECK)
        val secondId = second.assignment!!.id
        val itemId = support.item(fixture, packageId, fixture.documentRequirementId)

        val ambiguous = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewFindings.record(support.findingCommand(fixture, review, itemId)) }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_AMBIGUOUS, ambiguous.reasonCode)

        val namedCommand =
            support.findingCommand(fixture, review, itemId, assignmentId = secondId, key = "named-finding")
        val named = QuarkusTransaction.requiringNew().call { services.reviewFindings.record(namedCommand) }
        assertEquals(secondId, named.finding!!.assignmentId)
        assertEquals(secondId, named.assignment!!.id)

        val drafted = support.draft(services, fixture, second, InformationRequestReviewOutcome.CHANGES_REQUIRED)
        val recorded = support.record(services, fixture, second, drafted, "return-second-check")
        assertEquals(secondId, recorded.assignment!!.id)
        val replayedWorksheet = support.record(services, fixture, second, drafted, "return-second-check")
        assertEquals(secondId, replayedWorksheet.assignment!!.id)

        val replayedFinding = QuarkusTransaction.requiringNew().call { services.reviewFindings.record(namedCommand) }
        assertEquals(named.finding.id, replayedFinding.finding!!.id)
        assertEquals(secondId, replayedFinding.assignment?.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewFindings.record(namedCommand.copy(assignmentId = null)) }
        }
    }

    @Test
    fun `an unnamed finding binds to the one assignment still open for the item and a named one must be the caller's open assignment covering it`() {
        val colleagueParty = UUID.randomUUID()
        val colleagueUser = UUID.randomUUID()
        val fixture = twoStageFixture("SEQUENTIAL", colleagueParty to colleagueUser)
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueUser)),
        )
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val firstAssigned = support.assign(services, fixture, review, stageKey = FIRST_CHECK)
        val firstStage = firstAssigned.assignment!!
        val secondStage = support.assign(services, fixture, review, stageKey = SECOND_CHECK).assignment!!
        val colleague =
            support.assign(services, fixture, review, partyId = colleagueParty, stageKey = FIRST_CHECK).assignment!!
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val attestationItem = support.item(fixture, packageId, fixture.attestationRequirementId)
        fun record(command: RecordInformationRequestReviewFindingCommand) =
            QuarkusTransaction.requiringNew().call { services.reviewFindings.record(command) }

        fun refusal(command: RecordInformationRequestReviewFindingCommand) =
            assertThrows(InformationRequestLifecycleException::class.java) { record(command) }.reasonCode

        assertThrows(ForbiddenException::class.java) {
            record(support.findingCommand(fixture, review, documentItem, assignmentId = colleague.id))
        }
        assertThrows(ForbiddenException::class.java) {
            record(
                support.findingCommand(
                    fixture,
                    review,
                    documentItem,
                    reviewerAccess = support.owner(fixture),
                    assignmentId = firstStage.id
                )
            )
        }
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_STAGE_NOT_OPEN,
            refusal(support.findingCommand(fixture, review, documentItem, assignmentId = secondStage.id)),
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED,
            refusal(support.findingCommand(fixture, review, attestationItem, assignmentId = firstStage.id)),
        )
        QuarkusTransaction.requiringNew().run {
            services.reviewAssignments.change(
                support.changeCommand(
                    fixture,
                    colleague,
                    InformationRequestReviewAssignmentChange.RECUSAL,
                    support.access(colleagueUser)
                ),
            )
        }
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE,
            refusal(
                support.findingCommand(
                    fixture,
                    review,
                    documentItem,
                    reviewerAccess = support.access(colleagueUser),
                    assignmentId = colleague.id
                )
            ),
        )
        assertEquals(
            firstStage.id,
            record(
                support.findingCommand(
                    fixture,
                    review,
                    documentItem,
                    assignmentId = firstStage.id
                )
            ).finding!!.assignmentId
        )

        support.record(
            services,
            fixture,
            firstAssigned,
            support.draft(services, fixture, firstAssigned, InformationRequestReviewOutcome.SATISFIED),
            "accept-first-check",
        )
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED,
            refusal(support.findingCommand(fixture, review, documentItem, assignmentId = firstStage.id)),
        )
        val unnamed = record(support.findingCommand(fixture, review, documentItem))
        assertEquals(secondStage.id, unnamed.finding!!.assignmentId)
        assertEquals(secondStage.id, unnamed.assignment!!.id)

        val administrator =
            record(support.findingCommand(fixture, review, documentItem, reviewerAccess = support.owner(fixture)))
        assertNull(administrator.finding!!.assignmentId)
        assertNull(administrator.assignment)
    }

    @Test
    fun `a finding or comment replayed with its key returns the recorded one without a second row or history and a changed body is refused`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)
        val itemId = support.item(fixture, packageId, fixture.documentRequirementId)

        val findingCommand = support.findingCommand(fixture, review, itemId, key = "finding-once")
        val finding = QuarkusTransaction.requiringNew().call { services.reviewFindings.record(findingCommand) }
        val replayedFinding = QuarkusTransaction.requiringNew().call { services.reviewFindings.record(findingCommand) }
        assertEquals(finding.finding!!.id, replayedFinding.finding!!.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewFindings.record(findingCommand.copy(narrative = "A different account of what was found"))
            }
        }

        val commentCommand = RecordInformationRequestReviewCommentCommand(
            requestId = fixture.requestId,
            reviewId = review.id,
            submissionItemId = itemId,
            findingId = finding.finding.id,
            visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
            body = "Checked the note against the request",
            access = support.reviewer(fixture),
            idempotencyKey = "comment-once",
        )
        val comment = QuarkusTransaction.requiringNew().call { services.reviewComments.record(commentCommand) }
        val replayedComment = QuarkusTransaction.requiringNew().call { services.reviewComments.record(commentCommand) }
        assertEquals(comment.comment!!.id, replayedComment.comment!!.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewComments.record(commentCommand.copy(body = "A different note")) }
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(listOf(finding.finding.id), findingRepository.findForReview(review.id).map { it.id })
            assertEquals(
                listOf(comment.comment.id),
                commentRepository.findForReviews(listOf(review.id)).map { it.id })
            val mutations = transitionRepository.findForRequest(fixture.requestId).map { it.mutation }
            assertEquals(1, mutations.count { it == InformationRequestMutation.RECORD_FINDING })
            assertEquals(1, mutations.count { it == InformationRequestMutation.RECORD_REVIEW_COMMENT })
        }
        assertEquals(1, auditEvents("information_request.review.finding", fixture.requestId))
        assertEquals(1, auditEvents("information_request.review.comment", fixture.requestId))
    }

    private fun twoStageFixture(ordering: String, colleague: Pair<UUID, UUID>? = null): ReviewFixture =
        support.fixture(
            beforePublish = { connection, runtime ->
                orderReviewStages(connection, runtime.template.versionId, ordering)
                insertReviewStage(connection, runtime.template.versionId, FIRST_CHECK, 1)
                insertReviewStage(connection, runtime.template.versionId, SECOND_CHECK, 2)
            },
            prepare = { runtime ->
                colleague?.let { (partyId, userId) ->
                    runtime.insertActingParty(
                        partyId,
                        "REVIEWER",
                        userId
                    )
                }
            },
        )

    private fun auditEvents(eventTypeKey: String, requestId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM audit_outbox WHERE event_type_key = ? AND target_id = ?",
                eventTypeKey,
                requestId.toString(),
            )
        }

    private companion object {
        const val FIRST_CHECK = "first-check"
        const val SECOND_CHECK = "second-check"
    }
}
