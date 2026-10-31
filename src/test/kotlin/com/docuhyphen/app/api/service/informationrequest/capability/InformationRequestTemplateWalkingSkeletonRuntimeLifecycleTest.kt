package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestSuccessorResult
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestSummary
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPreview
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRemediationRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.ALTERNATE_EVIDENCE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.CONFIRMATION_STAGE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.CONTENT_REVIEW
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.EVIDENCE_STAGE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.PRIMARY_EVIDENCE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RECORDED_SUMMARY
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RESPONSE_CONFIRMATION
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.RESPONSE_STAGE
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUBMITTER_ATTESTATION
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateWalkingSkeletonRuntime.Companion.SUPPORTING_RECORD
import com.docuhyphen.app.api.service.informationrequest.capability.model.IssuedWalkingSkeleton
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceReviewSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeSummaryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionIncompleteException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonRuntimeLifecycleTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var evidenceUploads: InformationRequestEvidenceUploadService

    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var correctionRepository: InformationRequestCorrectionRepository

    @Inject
    lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository

    @Inject
    lateinit var remediationRepository: InformationRequestReviewRemediationRepository

    @Inject
    lateinit var progressService: InformationRequestCompletenessProgressService

    @Inject
    lateinit var summaries: InformationRequestExchangeSummaryService

    @Inject
    lateinit var workspaces: InformationRequestResponseWorkspaceService

    @Inject
    lateinit var authorizationService: AuthorizationService

    private val published by lazy {
        PublishedRequestSupport(
            dataSource,
            requestRepository,
            requirementRepository,
            definitionRepository,
            versionRepository,
            capabilityRepository,
            configurationWriter,
            materializer,
        )
    }
    private val skeletons by lazy {
        InformationRequestTemplateWalkingSkeletonRuntime(
            dataSource,
            published,
            runtime,
            requestRepository,
            evidenceUploads,
            artifactRepository
        )
    }
    private val reviews by lazy { ConformanceReviewSupport(runtime, reviewRepository) }

    @Test
    fun `a basic fixture is assented and submitted into one closed package, refuses further answers, and recurs for its manager from that package`() {
        val issued = skeletons.issueBasic(listOf(CONTRIBUTOR, ATTESTOR))
        val request = issued.request
        val services = runtime.build(issued.requestId, centralAuthorization = authorizationService)
        val contributor = request.access(CONTRIBUTOR)
        val attestor = request.access(ATTESTOR)

        skeletons.respond(
            services, issued, RECORDED_SUMMARY, contributor, "answer-summary",
            disposition = InformationRequestResponseDisposition.PROVIDED, fieldValue = "Recorded summary",
        )
        skeletons.upload(issued, SUPPORTING_RECORD, contributor, "supporting-record-file")
        skeletons.respond(
            services,
            issued,
            SUPPORTING_RECORD,
            contributor,
            "provide-record",
            disposition = InformationRequestResponseDisposition.PROVIDED
        )
        val contributorView = preview(services, issued, null, contributor)
        assertEquals(emptyList<Pair<String, InformationRequestSubmissionProblemCode>>(), problems(contributorView))
        assertEquals(1, contributorView.readiness.undisclosedProblemCount)
        assertEquals(
            listOf(RESPONSE_CONFIRMATION to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING),
            problems(preview(services, issued, null, attestor)),
        )
        assertThrows(InformationRequestSubmissionIncompleteException::class.java) {
            skeletons.submit(
                services,
                issued,
                contributor,
                "early-submit"
            )
        }

        val assent = skeletons.assent(services, issued, RESPONSE_CONFIRMATION, attestor, "confirm-response")
        assertEquals(InformationRequestAttestationState.SATISFIED, assent.evaluation?.evaluation?.state)
        val submitted = skeletons.submit(services, issued, contributor, "submit")
        val submission = submitted.submission.submissionPackage
        assertEquals(InformationRequestState.CLOSED, submitted.request.state)
        assertEquals(submission.id, submitted.request.satisfiedByPackageId)
        assertFalse(submission.reviewRequired)
        assertTrue(submission.completesRequest)
        assertEquals(
            issued.fixture.expectedRequirementKeys.toSet(),
            submitted.submission.items.map { it.requirementKey }.toSet()
        )
        assertEquals(listOf(assent.attestation.id), submitted.submission.attestations.map { it.id })
        assertTrue(reviews.reviews(issued.requestId).isEmpty())

        val closed = assertThrows(InformationRequestLifecycleException::class.java) {
            skeletons.respond(
                services,
                issued,
                RECORDED_SUMMARY,
                contributor,
                "after-closure",
                fieldValue = "Changed summary"
            )
        }
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, closed.reasonCode)
        assertThrows(ForbiddenException::class.java) {
            defineRecurrence(
                services,
                issued,
                contributor,
                "contributor-recurrence"
            )
        }

        val recurrence = defineRecurrence(services, issued, request.owner, "define-recurrence")
        val first = nextOccurrence(services, issued, recurrence, request.owner, "first-occurrence")
        assertEquals(InformationRequestLineageKind.RECURRENCE, first.lineage.lineageKind)
        assertEquals(1, first.lineage.recurrenceSequence)
        assertEquals(submission.id, first.lineage.sourcePackageId)
        assertEquals(InformationRequestState.CLOSED, first.source.state)
        assertEquals(InformationRequestState.DRAFT, first.successor.state)
        assertEquals(request.templateVersionId, first.successor.templateVersionId)
        val sourceKeys = submitted.submission.items.associate { it.id to it.requirementKey }
        assertEquals(
            setOf(
                RECORDED_SUMMARY to InformationRequestCarryForwardDecision.OFFERED,
                SUPPORTING_RECORD to InformationRequestCarryForwardDecision.INVALIDATED,
                RESPONSE_CONFIRMATION to InformationRequestCarryForwardDecision.INVALIDATED,
            ),
            first.carryForwards.map { sourceKeys.getValue(it.sourceItemId) to it.decision }.toSet(),
        )
        val notDue = assertThrows(InformationRequestLifecycleException::class.java) {
            nextOccurrence(services, issued, recurrence, request.owner, "second-occurrence")
        }
        assertEquals(InformationRequestErrorCatalog.RECURRENCE_NOT_DUE, notDue.reasonCode)

        val listed = summary(issued, contributor)
        assertEquals(InformationRequestState.CLOSED, listed.request.state)
        assertEquals(2 to 2, listed.completedCount to listed.requiredCount)
        val header = QuarkusTransaction.requiringNew().call { workspaces.loadRequest(issued.requestId, request.owner) }
        assertEquals(InformationRequestState.CLOSED, header.state)
        assertNotNull(header.closedAt)
        assertEquals(Triple(3, 3, 100), progress(issued))
    }

    @Test
    fun `a multi-party fixture submits its stages in order, corrects a waived record, and completes after its acting parties confirm`() {
        val issued = skeletons.issueMultiParty(
            listOf(
                SUBJECT,
                CONTRIBUTOR,
                ATTESTOR,
                FIRST_REVIEWER,
                SECOND_REVIEWER,
                THIRD_REVIEWER
            )
        )
        val request = issued.request
        val services = runtime.build(issued.requestId, centralAuthorization = authorizationService)
        val contributor = request.access(CONTRIBUTOR)
        val attestor = request.access(ATTESTOR)

        val outOfOrder = assertThrows(InformationRequestLifecycleException::class.java) {
            skeletons.submit(services, issued, contributor, "evidence-first", EVIDENCE_STAGE)
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_STAGE_ORDER, outOfOrder.reasonCode)
        val responses = skeletons.submit(services, issued, contributor, "submit-responses", RESPONSE_STAGE)
        assertEquals(emptyList<String>(), responses.submission.items.map { it.requirementKey })
        assertFalse(responses.submission.submissionPackage.reviewRequired)

        skeletons.respond(
            services,
            issued,
            PRIMARY_EVIDENCE,
            contributor,
            "waive-primary",
            disposition = InformationRequestResponseDisposition.WAIVED,
            narrative = ResponseNarrativePatch.Set("The primary records are not issued for this period"),
        )
        skeletons.upload(issued, ALTERNATE_EVIDENCE, contributor, "alternate-record-file")
        skeletons.respond(
            services,
            issued,
            ALTERNATE_EVIDENCE,
            contributor,
            "provide-alternate",
            disposition = InformationRequestResponseDisposition.PROVIDED
        )
        val evidence = skeletons.submit(services, issued, contributor, "submit-evidence", EVIDENCE_STAGE)
        val evidencePackage = evidence.submission.submissionPackage
        assertTrue(evidencePackage.reviewRequired)
        assertEquals(
            mapOf(
                PRIMARY_EVIDENCE to InformationRequestEvidenceRequirementState.WAIVER_REQUESTED.name,
                ALTERNATE_EVIDENCE to InformationRequestEvidenceRequirementState.SATISFIED.name,
            ),
            evidence.submission.items.associate { it.requirementKey to it.evidenceState },
        )

        val review = reviews.reviews(issued.requestId).single()
        assertEquals(evidencePackage.id, review.packageId)
        val primary = skeletons.requirementId(issued, PRIMARY_EVIDENCE)
        val primaryItem = reviews.item(issued.requestId, evidencePackage.id, primary)
        assertEquals(listOf(primaryItem), reviews.coverage(review.id, CONTENT_REVIEW))
        assertEquals(emptyList<UUID>(), reviews.coverage(review.id, CONFIRMATION_REVIEW))
        val assigned = listOf(FIRST_REVIEWER, SECOND_REVIEWER, THIRD_REVIEWER).associateWith { reviewer ->
            reviews.assign(
                services,
                issued.requestId,
                review.id,
                CONTENT_REVIEW,
                request.parties.getValue(reviewer),
                request.owner,
                "assign-$reviewer"
            )
        }
        val findings = listOf(FIRST_REVIEWER, SECOND_REVIEWER).map { reviewer ->
            reviews.finding(
                services, issued.requestId, review.id, primaryItem, InformationRequestFindingSeverity.MAJOR,
                InformationRequestFindingCorrectionScope.RESPONSE, request.access(reviewer), "finding-$reviewer",
            )
        }
        val firstDecision = reviews.decide(
            services,
            issued.requestId,
            assigned.getValue(FIRST_REVIEWER),
            mapOf(primaryItem to InformationRequestReviewOutcome.CHANGES_REQUIRED),
            request.access(FIRST_REVIEWER),
            "decide-$FIRST_REVIEWER",
        )
        assertFalse(firstDecision.review.state.settled, "one decision does not reach the content quorum")
        val returned = reviews.decide(
            services,
            issued.requestId,
            assigned.getValue(SECOND_REVIEWER),
            mapOf(primaryItem to InformationRequestReviewOutcome.CHANGES_REQUIRED),
            request.access(SECOND_REVIEWER),
            "decide-$SECOND_REVIEWER",
        )
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        assertEquals(InformationRequestState.IN_PROGRESS, returned.request.state)
        QuarkusTransaction.requiringNew().run {
            val correction = correctionRepository.findForRequest(issued.requestId).single()
            assertEquals(
                listOf(primary),
                correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId })
        }

        val outside = assertThrows(InformationRequestLifecycleException::class.java) {
            skeletons.respond(
                services,
                issued,
                ALTERNATE_EVIDENCE,
                contributor,
                "outside-correction",
                narrative = ResponseNarrativePatch.Set("Changed outside the correction")
            )
        }
        assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, outside.reasonCode)
        skeletons.upload(
            issued,
            PRIMARY_EVIDENCE,
            contributor,
            "first-period-file",
            attributed(startsDaysAgo = 60, endsDaysAgo = 40)
        )
        skeletons.upload(
            issued,
            PRIMARY_EVIDENCE,
            contributor,
            "second-period-file",
            attributed(startsDaysAgo = 39, endsDaysAgo = 20)
        )
        skeletons.respond(
            services, issued, PRIMARY_EVIDENCE, contributor, "provide-primary",
            disposition = InformationRequestResponseDisposition.PROVIDED, narrative = ResponseNarrativePatch.Clear,
        )
        val corrected = skeletons.submit(services, issued, contributor, "resubmit-evidence", EVIDENCE_STAGE)
        assertEquals(evidencePackage.id, corrected.submission.submissionPackage.previousPackageId)
        assertFalse(corrected.submission.submissionPackage.reviewRequired)
        assertEquals(
            InformationRequestEvidenceRequirementState.SATISFIED.name,
            corrected.submission.items.single { it.requirementKey == PRIMARY_EVIDENCE }.evidenceState,
        )
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestCorrectionState.RESUBMITTED,
                correctionRepository.findForRequest(issued.requestId).single().state
            )
            assertEquals(
                findings.map { it.id }.toSet(),
                remediationRepository.findForRequest(issued.requestId).map { it.findingId }.toSet()
            )
        }
        assertEquals(listOf(review.id), reviews.reviews(issued.requestId).map { it.id })

        val awaiting = preview(services, issued, CONFIRMATION_STAGE, attestor).attestations.single()
        assertEquals(SUBMITTER_ATTESTATION, awaiting.requirementKey)
        assertEquals(
            listOf(InformationRequestContributorRole.CONTRIBUTOR, InformationRequestContributorRole.ATTESTOR),
            awaiting.evaluated.evaluation.missingRoles,
        )
        val unordered = assertThrows(InformationRequestLifecycleException::class.java) {
            skeletons.assent(services, issued, SUBMITTER_ATTESTATION, attestor, "attestor-assent", CONFIRMATION_STAGE)
        }
        assertEquals(InformationRequestErrorCatalog.ATTESTATION_ORDER_UNMET, unordered.reasonCode)
        assertThrows(InformationRequestSubmissionIncompleteException::class.java) {
            skeletons.submit(services, issued, contributor, "submit-confirmation", CONFIRMATION_STAGE)
        }

        val contributorListing = summary(issued, contributor)
        assertEquals(InformationRequestState.IN_PROGRESS, contributorListing.request.state)
        assertEquals(2 to 3, contributorListing.completedCount to contributorListing.requiredCount)
        val attestorListing = summary(issued, attestor)
        assertEquals(0 to 1, attestorListing.completedCount to attestorListing.requiredCount)
        assertEquals(Triple(2, 3, 66), progress(issued))

        val firstAssent = skeletons.assent(
            services,
            issued,
            SUBMITTER_ATTESTATION,
            contributor,
            "contributor-assent",
            CONFIRMATION_STAGE
        )
        assertEquals(InformationRequestAttestationState.PENDING, firstAssent.evaluation?.evaluation?.state)
        val finalAssent = skeletons.assent(
            services,
            issued,
            SUBMITTER_ATTESTATION,
            attestor,
            "ordered-attestor-assent",
            CONFIRMATION_STAGE
        )
        assertEquals(InformationRequestAttestationState.SATISFIED, finalAssent.evaluation?.evaluation?.state)
        val finalSubmission =
            skeletons.submit(services, issued, contributor, "submit-completed-confirmation", CONFIRMATION_STAGE)
        assertEquals(listOf(SUBMITTER_ATTESTATION), finalSubmission.submission.items.map { it.requirementKey })
        assertEquals(
            setOf(firstAssent.attestation.id, finalAssent.attestation.id),
            finalSubmission.submission.attestations.map { it.id }.toSet()
        )
        assertEquals(InformationRequestState.CLOSED, finalSubmission.request.state)
        assertEquals(Triple(3, 3, 100), progress(issued))
    }

    private fun defineRecurrence(
        services: InformationRequestRuntimeServices,
        issued: IssuedWalkingSkeleton,
        access: RequestAccessContext,
        key: String,
    ): InformationRequestRecurrence {
        val precondition = CommandPrecondition.ExpectedRevision(skeletons.aggregateETag(issued))
        return QuarkusTransaction.requiringNew().call {
            services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = issued.requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(1)),
                    maximumOccurrences = 2,
                    access = access,
                    precondition = precondition,
                    idempotencyKey = key,
                ),
            ).recurrence
        }
    }

    private fun nextOccurrence(
        services: InformationRequestRuntimeServices,
        issued: IssuedWalkingSkeleton,
        recurrence: InformationRequestRecurrence,
        access: RequestAccessContext,
        key: String,
    ): InformationRequestSuccessorResult =
        QuarkusTransaction.requiringNew().call {
            services.followUps.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    issued.requestId,
                    recurrence.id,
                    access,
                    key
                )
            )
        }

    private fun preview(
        services: InformationRequestRuntimeServices,
        issued: IssuedWalkingSkeleton,
        stageKey: String?,
        access: RequestAccessContext,
    ): InformationRequestSubmissionPreview =
        QuarkusTransaction.requiringNew()
            .call { services.submissionQueries.preview(issued.requestId, stageKey, access) }

    private fun problems(preview: InformationRequestSubmissionPreview): List<Pair<String, InformationRequestSubmissionProblemCode>> =
        preview.readiness.problems.map { it.requirementKey to it.code }

    private fun summary(issued: IssuedWalkingSkeleton, access: RequestAccessContext): InformationRequestSummary =
        QuarkusTransaction.requiringNew().call { summaries.listForExchange(issued.request.exchangeId, access) }
            .requests.single { it.request.id == issued.requestId }

    private fun progress(issued: IssuedWalkingSkeleton): Triple<Int, Int, Int> =
        QuarkusTransaction.requiringNew().call { progressService.evaluate(issued.requestId) }
            .let { Triple(it.completedCount, it.totalCount, it.percentComplete) }

    private fun attributed(startsDaysAgo: Long, endsDaysAgo: Long): InformationRequestEvidenceAttributes {
        val today = LocalDate.now(ZoneOffset.UTC)
        return InformationRequestEvidenceAttributes(
            issuer = "recording-party",
            language = "process-language",
            issuedOn = today.minusDays(10),
            expiresOn = today.plusDays(100),
            coverage = InformationRequestEvidenceCoverage(today.minusDays(startsDaysAgo), today.minusDays(endsDaysAgo)),
            certificationReference = "certification-reference",
        )
    }

    private companion object {
        const val SUBJECT = "SUBJECT"
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val ATTESTOR = "ATTESTOR"
        const val FIRST_REVIEWER = "REVIEWER#first"
        const val SECOND_REVIEWER = "REVIEWER#second"
        const val THIRD_REVIEWER = "REVIEWER#third"
        const val CONFIRMATION_REVIEW = "confirmation-review"
    }
}
