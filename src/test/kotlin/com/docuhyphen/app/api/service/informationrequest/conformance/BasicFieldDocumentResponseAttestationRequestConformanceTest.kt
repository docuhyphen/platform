package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.refusedBy
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionIncompleteException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class BasicFieldDocumentResponseAttestationRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a sparse draft completes one Field, Document, and Response Attestation, submits one exact package, and closes atomically without review`()
    {
        val request = support.fieldRequest()
        val services = runtime.build(request.requestId)

        support.saveNarrative(services, request, request.answers.requirementId, "Draft note only", "sparse-draft")
        assertEquals(
            setOf(
                request.answers.requirementId to InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE,
                request.runtime.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING,
            ),
            problems(services, request),
        )
        val early = assertThrows(InformationRequestSubmissionIncompleteException::class.java) { support.submit(services, request, "early") }
        assertFalse(early.readiness.ready)

        support.answerField(services, request, "Recorded answer", "answer-field")
        assertEquals(
            setOf(request.runtime.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING),
            problems(services, request),
        )
        val attested = support.assent(services, request, "assent")
        val contentBeforeSubmission = support.submissionETag(request)
        assertEquals(contentBeforeSubmission, attested.submissionETag)

        val submitted = support.submit(services, request, "submit")

        val submission = submitted.submission.submissionPackage
        assertEquals(InformationRequestState.CLOSED, submitted.request.state)
        assertEquals(submission.id, submitted.request.satisfiedByPackageId)
        assertFalse(submission.reviewRequired)
        assertTrue(submission.completesRequest)
        assertEquals(
            setOf(request.answers.requirementId, request.runtime.documentRequirementId, request.runtime.attestationRequirementId),
            submitted.submission.items.map { it.informationRequestRequirementId }.toSet(),
        )
        val fieldItem = submitted.submission.items.single { it.informationRequestRequirementId == request.answers.requirementId }
        assertEquals(
            fieldItem.fieldValueRevisionId.toString(),
            dataSource.connection.use { connection ->
                queryString(
                    connection,
                    "SELECT id::text FROM field_value_revision WHERE schema_assignment_id = ? ORDER BY revision_number DESC LIMIT 1",
                    request.answers.assignmentId,
                )
            },
        )
        assertEquals(listOf(request.runtime.evidenceVersionId), submitted.submission.evidence.map { it.evidenceVersionId })
        assertEquals(listOf(attested.attestation.id), submitted.submission.attestations.map { it.id })
        assertEquals(contentBeforeSubmission.substringAfterLast(':').trimEnd('"'), submission.contentHashSha256)
        QuarkusTransaction.requiringNew().run {
            assertTrue(reviewRepository.findForRequest(request.requestId).isEmpty())
            val mutations = transitionRepository.findForRequest(request.requestId).map { it.mutation }
            assertTrue(mutations.containsAll(listOf(InformationRequestMutation.SUBMIT, InformationRequestMutation.CLOSE)), mutations.toString())
        }

        val replayed = support.submit(services, request, "submit")
        assertEquals(submission.id, replayed.submission.submissionPackage.id)
        val afterClosure = assertThrows(InformationRequestLifecycleException::class.java) {
            support.answerField(services, request, "Changed answer", "after-closure")
        }
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, afterClosure.reasonCode)
        dataSource.connection.use { connection ->
            refusedBy(connection, "append-only") {
                execute(connection, "UPDATE information_request_submission_item SET item_hash_sha256 = ? WHERE id = ?", "0".repeat(64), fieldItem.id)
            }
            refusedBy(connection, "append-only") {
                execute(connection, "DELETE FROM information_request_submission_package WHERE id = ?", submission.id)
            }
        }
    }

    private fun problems(
        services: com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices,
        request: ConformanceRequest,
    ) = QuarkusTransaction.requiringNew().call {
        services.submissionQueries.preview(request.requestId, null, support.contributor(request)).readiness.problems
            .map { it.requirementId to it.code }
            .toSet()
    }
}
