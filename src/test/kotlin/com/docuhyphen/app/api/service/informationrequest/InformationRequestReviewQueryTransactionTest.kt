package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewQueryTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository

    private val support by lazy { InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository) }

    @Test
    fun `respondents see only respondent-visible findings, and only once the review settles`()
    {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val itemId = support.item(fixture, packageId, fixture.documentRequirementId)
        val shared = support.finding(services, fixture, review, itemId, InformationRequestFindingCorrectionScope.RESPONSE)
        support.finding(
            services,
            fixture,
            review,
            itemId,
            InformationRequestFindingCorrectionScope.NONE,
            visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
        )

        val pending = QuarkusTransaction.requiringNew().call { services.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }
        assertTrue(pending.single().visibleFindings.isEmpty())
        val early = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { services.reviewComments.record(respondentComment(fixture, review.id, itemId, shared.id)) }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, early.reasonCode)

        support.record(services, fixture, assigned, support.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED), "return")
        QuarkusTransaction.requiringNew().run {
            services.reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = fixture.requestId,
                    reviewId = review.id,
                    submissionItemId = itemId,
                    visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
                    body = "Kept among reviewers",
                    access = support.reviewer(fixture),
                    idempotencyKey = "reviewer-note",
                ),
            )
        }
        val answered = QuarkusTransaction.requiringNew().call {
            services.reviewComments.record(respondentComment(fixture, review.id, itemId, shared.id))
        }
        assertEquals(InformationRequestReviewVisibility.RESPONDENT_VISIBLE, answered.comment!!.visibility)

        val settled = QuarkusTransaction.requiringNew().call { services.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }.single()
        assertEquals(listOf(shared.id), settled.visibleFindings.map { it.id })
        assertEquals(listOf(answered.comment!!.id), settled.visibleComments.map { it.id })
        assertEquals(listOf(fixture.documentRequirementId), settled.correction!!.items.map { it.requirementId })
        assertTrue(settled.canAppeal)
        assertTrue(settled.canComment)
    }

    @Test
    fun `a reviewer reads the content it may review while an administrator reads only the review's standing`()
    {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)

        val asReviewer = QuarkusTransaction.requiringNew().call { services.reviewQueries.review(fixture.requestId, review.id, support.reviewer(fixture)) }
        assertTrue(asReviewer.contentVisibleItemIds.isNotEmpty())
        assertEquals(1, asReviewer.callerAssignmentIds.size)
        assertFalse(asReviewer.canManage)

        val asOwner = QuarkusTransaction.requiringNew().call { services.reviewQueries.review(fixture.requestId, review.id, support.owner(fixture)) }
        assertTrue(asOwner.contentVisibleItemIds.isEmpty())
        assertTrue(asOwner.callerAssignmentIds.isEmpty())
        assertTrue(asOwner.canManage)

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { services.reviewQueries.reviews(fixture.requestId, support.contributor(fixture)) }
        }
    }

    @Test
    fun `the reviewer queue lists open assignments by due instant and drops decided ones`()
    {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val due = Instant.now().plus(Duration.ofDays(3))
        val assigned = support.assign(services, fixture, review, dueAt = due)

        val queued = QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(support.reviewer(fixture)) }
        assertEquals(listOf(assigned.assignment!!.id), queued.map { it.assignment.id })
        assertEquals(due.epochSecond, queued.single().assignment.dueAt!!.toInstant().epochSecond)
        assertEquals(1, queued.single().itemCount)

        support.record(services, fixture, assigned, support.draft(services, fixture, assigned, InformationRequestReviewOutcome.SATISFIED), "accept")
        assertTrue(QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(support.reviewer(fixture)) }.isEmpty())
    }

    private fun respondentComment(fixture: ReviewFixture, reviewId: UUID, itemId: UUID, findingId: UUID) =
        RecordInformationRequestReviewCommentCommand(
            requestId = fixture.requestId,
            reviewId = reviewId,
            submissionItemId = itemId,
            findingId = findingId,
            body = "The note now states what was asked",
            access = support.contributor(fixture),
            idempotencyKey = "respondent-reply-${UUID.randomUUID()}",
        )
}
