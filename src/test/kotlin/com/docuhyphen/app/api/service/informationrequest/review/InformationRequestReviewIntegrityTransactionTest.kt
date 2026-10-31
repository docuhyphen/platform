package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewAssignmentChange
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewFindingCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
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
class InformationRequestReviewIntegrityTransactionTest {
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
    fun `a reviewer is not assigned to a stage that has already settled`() {
        val secondPartyId = UUID.randomUUID()
        val secondUserId = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, configured ->
                orderReviewStages(connection, configured.template.versionId, "SEQUENTIAL")
                insertReviewStage(connection, configured.template.versionId, FIRST_CHECK, 1)
                insertReviewStage(connection, configured.template.versionId, SECOND_CHECK, 2)
            },
            prepare = { runtimeFixture -> runtimeFixture.insertActingParty(secondPartyId, "REVIEWER", secondUserId) },
        )
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, secondUserId))
        )
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val first = support.assign(services, fixture, review, stageKey = FIRST_CHECK)
        val settled = support.record(
            services,
            fixture,
            first,
            support.draft(services, fixture, first, InformationRequestReviewOutcome.SATISFIED),
            "first-check"
        )
        assertEquals(InformationRequestReviewState.IN_REVIEW, settled.review.state)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            support.assign(services, fixture, review, partyId = secondPartyId, stageKey = FIRST_CHECK)
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_STAGE_NOT_OPEN, refusal.reasonCode)
        support.assign(services, fixture, review, partyId = secondPartyId, stageKey = SECOND_CHECK)
    }

    @Test
    fun `a retest names an earlier finding about the same Requirement`() {
        val fixture =
            support.fixture(beforePublish = { connection, configured -> reviewAttestation(connection, configured) })
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val firstPackage = support.submitWhole(services, fixture)
        val first = support.reviewsOf(fixture).single()
        val firstAssigned = support.assign(services, fixture, first)
        val documentFinding = support.finding(
            services,
            fixture,
            first,
            support.item(fixture, firstPackage, fixture.documentRequirementId),
            InformationRequestFindingCorrectionScope.RESPONSE,
        )
        support.record(
            services,
            fixture,
            firstAssigned,
            support.draft(
                services,
                fixture,
                firstAssigned,
                outcomes = mapOf(
                    support.item(
                        fixture,
                        firstPackage,
                        fixture.documentRequirementId
                    ) to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                    support.item(
                        fixture,
                        firstPackage,
                        fixture.attestationRequirementId
                    ) to InformationRequestReviewOutcome.SATISFIED,
                ),
            ),
            "return-document",
        )
        support.patchNarrative(services, fixture, fixture.documentRequirementId, "The corrected note")
        val secondPackage = support.submitWhole(services, fixture)
        val retest = support.reviewsOf(fixture).last()
        support.assign(services, fixture, retest)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            retestFinding(
                services,
                fixture,
                retest,
                support.item(fixture, secondPackage, fixture.attestationRequirementId),
                documentFinding.id,
                "retest-other-item"
            )
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_RETEST_INVALID, refusal.reasonCode)
        retestFinding(
            services,
            fixture,
            retest,
            support.item(fixture, secondPackage, fixture.documentRequirementId),
            documentFinding.id,
            "retest-same-item"
        )
    }

    @Test
    fun `an assignment change retried under one key with another narrative or If-Match is refused rather than replayed`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val assigned = support.assign(services, fixture, support.reviewsOf(fixture).single())
        val recusal = support.changeCommand(
            fixture,
            requireNotNull(assigned.assignment),
            InformationRequestReviewAssignmentChange.RECUSAL,
            support.reviewer(fixture),
            key = "recuse-once",
        )
        val recused = QuarkusTransaction.requiringNew().call { services.reviewAssignments.change(recusal) }

        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewAssignments.change(recusal.copy(narrative = "Another account of the recusal")) }
        }
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewAssignments.change(
                    recusal.copy(precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(recusal.reviewId))),
                )
            }
        }
        assertEquals(
            recused.assignment?.id,
            QuarkusTransaction.requiringNew().call { services.reviewAssignments.change(recusal) }.assignment?.id,
        )
    }

    @Suppress("LongParameterList")
    private fun retestFinding(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        review: InformationRequestReview,
        itemId: UUID,
        retests: UUID,
        key: String,
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewFindings.record(
            RecordInformationRequestReviewFindingCommand(
                requestId = fixture.requestId,
                reviewId = review.id,
                submissionItemId = itemId,
                reasonCode = "record.checked",
                narrative = "The note now states what was asked",
                severity = InformationRequestFindingSeverity.OBSERVATION,
                visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                correctionScope = InformationRequestFindingCorrectionScope.NONE,
                retestsFindingId = retests,
                retestResult = InformationRequestRetestResult.RESOLVED,
                access = support.reviewer(fixture),
                idempotencyKey = key,
            ),
        )
    }

    private companion object {
        const val FIRST_CHECK = "first-check"
        const val SECOND_CHECK = "second-check"
    }
}
