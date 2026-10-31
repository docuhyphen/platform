package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
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
class InformationRequestReviewCommentTargetTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a respondent cannot tell an item of a decided review it may not see from one the review does not hold`() {
        val returned = returnedReview()

        val hidden = assertThrows(ForbiddenException::class.java) {
            comment(returned, returned.attestationItemId)
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            comment(returned, UUID.randomUUID())
        }

        assertEquals(hidden.message, unknown.message)
    }

    @Test
    fun `a respondent cannot tell an item of an undecided review it may not see from one the review does not hold`() {
        val fixture = support.fixture()
        val setup = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(setup, fixture)
        val review = support.reviewsOf(fixture).single()
        val pending = ReturnedReview(
            fixture,
            review,
            support.item(fixture, packageId, fixture.documentRequirementId),
            support.item(fixture, packageId, fixture.attestationRequirementId),
            internalFindingId = UUID.randomUUID(),
        )

        val hidden = assertThrows(ForbiddenException::class.java) {
            comment(pending, pending.attestationItemId)
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            comment(pending, UUID.randomUUID())
        }
        val own = assertThrows(InformationRequestLifecycleException::class.java) {
            comment(pending, pending.documentItemId)
        }

        assertEquals(hidden.message, unknown.message)
        assertEquals(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, own.reasonCode)
    }

    @Test
    fun `a respondent cannot tell a finding kept from respondents from a finding that does not exist`() {
        val returned = returnedReview()

        val internal = assertThrows(InformationRequestLifecycleException::class.java) {
            comment(returned, returned.documentItemId, findingId = returned.internalFindingId)
        }
        val unknown = assertThrows(InformationRequestLifecycleException::class.java) {
            comment(returned, returned.documentItemId, findingId = UUID.randomUUID())
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, internal.reasonCode)
        assertEquals(internal.reasonCode, unknown.reasonCode)
        assertEquals(internal.message, unknown.message)
    }

    @Test
    fun `control a respondent comments on its own item of a decided review`() {
        val returned = returnedReview()

        val recorded = comment(returned, returned.documentItemId)

        assertEquals(returned.documentItemId, recorded.comment?.submissionItemId)
        assertEquals(InformationRequestReviewVisibility.RESPONDENT_VISIBLE, recorded.comment?.visibility)
    }

    private fun returnedReview(): ReturnedReview {
        val fixture = support.fixture()
        val setup = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(setup, fixture)
        val review = support.reviewsOf(fixture).single()
        val documentItemId = support.item(fixture, packageId, fixture.documentRequirementId)
        val assigned = support.assign(setup, fixture, review)
        support.finding(setup, fixture, review, documentItemId, InformationRequestFindingCorrectionScope.RESPONSE)
        val internal = support.finding(
            setup,
            fixture,
            review,
            documentItemId,
            InformationRequestFindingCorrectionScope.NONE,
            visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
        )
        support.record(
            setup,
            fixture,
            assigned,
            support.draft(setup, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-${UUID.randomUUID()}",
        )
        return ReturnedReview(
            fixture,
            review,
            documentItemId,
            support.item(fixture, packageId, fixture.attestationRequirementId),
            internal.id,
        )
    }

    private fun comment(returned: ReturnedReview, itemId: UUID, findingId: UUID? = null) =
        QuarkusTransaction.requiringNew().call {
            respondentServices(returned).reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = returned.fixture.requestId,
                    reviewId = returned.review.id,
                    submissionItemId = itemId,
                    findingId = findingId,
                    body = "A question about this item",
                    access = support.contributor(returned.fixture),
                    idempotencyKey = "comment-${UUID.randomUUID()}",
                ),
            )
        }

    private fun respondentServices(returned: ReturnedReview): InformationRequestRuntimeServices =
        runtime.build(
            returned.fixture.requestId,
            hiddenRequirementId = returned.fixture.attestationRequirementId,
            denies = support.roleDenials(returned.fixture),
        )

    private class ReturnedReview(
        val fixture: ReviewFixture,
        val review: InformationRequestReview,
        val documentItemId: UUID,
        val attestationItemId: UUID,
        val internalFindingId: UUID,
    )
}
