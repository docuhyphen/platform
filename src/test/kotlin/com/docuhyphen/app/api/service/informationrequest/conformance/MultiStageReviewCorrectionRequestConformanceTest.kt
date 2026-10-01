package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestRetestResult
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRemediationRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class MultiStageReviewCorrectionRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var materializer: InformationRequestTemplateMaterializer
    @Inject lateinit var occurrences: InformationRequestGroupOccurrenceService
    @Inject lateinit var contentCollector: InformationRequestSubmissionContentCollector
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var decisionRepository: InformationRequestReviewDecisionRepository
    @Inject lateinit var findingRepository: InformationRequestReviewFindingRepository
    @Inject lateinit var correctionRepository: InformationRequestCorrectionRepository
    @Inject lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository
    @Inject lateinit var remediationRepository: InformationRequestReviewRemediationRepository
    @Inject lateinit var authorizationService: AuthorizationService

    private val published by lazy {
        PublishedRequestSupport(
            dataSource, requestRepository, requirementRepository, definitionRepository, versionRepository, capabilityRepository,
            configurationWriter, materializer,
        )
    }
    private val reviews by lazy { ConformanceReviewSupport(runtime, reviewRepository) }

    @Test
    fun `sequential stages enforce separation of duties and reopen only the returned occurrence, which is remediated and retested`()
    {
        val request = published.issue("multi-stage-review-correction-request", listOf(NOTE, SUMMARY), listOf(CONTRIBUTOR, FIRST_REVIEWER, SECOND_REVIEWER)) { schemaVersionId, fields ->
            sequentialConfiguration(schemaVersionId, fields.getValue(NOTE).fieldDefinitionId, fields.getValue(SUMMARY).fieldDefinitionId)
        }
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val firstEntry = published.addOccurrence(occurrences, request, request.access(CONTRIBUTOR), ENTRY, null, "add-first-entry")
        val secondEntry = published.addOccurrence(occurrences, request, request.access(CONTRIBUTOR), ENTRY, null, "add-second-entry")
        val firstNote = published.requirement(request, NOTE, firstEntry.occurrencePath).id
        val secondNote = published.requirement(request, NOTE, secondEntry.occurrencePath).id
        val summary = published.requirement(request, SUMMARY).id
        answer(services, request, mapOf(firstNote to (NOTE to "first entry note"), secondNote to (NOTE to "second entry note"), summary to (SUMMARY to "summary")), "answers")

        val firstPackage = submit(services, request, "submit-first")
        val initial = reviews.reviews(request.requestId).single()
        val firstAssigned = reviews.assign(services, request.requestId, initial.id, FIRST_CHECK, request.parties.getValue(FIRST_REVIEWER), request.owner, "assign-first-check")
        val returnedItem = reviews.item(request.requestId, firstPackage, firstNote)
        val finding = reviews.finding(
            services, request.requestId, initial.id, returnedItem, InformationRequestFindingSeverity.MAJOR,
            InformationRequestFindingCorrectionScope.RESPONSE, request.access(FIRST_REVIEWER), "finding-first-note",
        )
        reviews.finding(
            services, request.requestId, initial.id, reviews.item(request.requestId, firstPackage, summary), InformationRequestFindingSeverity.OBSERVATION,
            InformationRequestFindingCorrectionScope.NONE, request.access(FIRST_REVIEWER), "observation-summary",
        )
        val returned = reviews.decide(
            services, request.requestId, firstAssigned,
            mapOf(
                returnedItem to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                reviews.item(request.requestId, firstPackage, secondNote) to InformationRequestReviewOutcome.SATISFIED,
                reviews.item(request.requestId, firstPackage, summary) to InformationRequestReviewOutcome.SATISFIED,
            ),
            request.access(FIRST_REVIEWER), "decide-first-check",
        )
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        assertEquals(InformationRequestState.IN_PROGRESS, returned.request.state)
        QuarkusTransaction.requiringNew().run {
            assertEquals(2, findingRepository.findForReview(initial.id).size)
            val correction = correctionRepository.findForRequest(request.requestId).single()
            assertEquals(listOf(firstNote), correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId })
        }

        listOf(secondNote to NOTE, summary to SUMMARY).forEach { (outside, fieldKey) ->
            val refused = assertThrows(InformationRequestLifecycleException::class.java) {
                answer(services, request, mapOf(outside to (fieldKey to "changed outside the correction")), "outside-$outside")
            }
            assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, refused.reasonCode)
        }
        answer(services, request, mapOf(firstNote to (NOTE to "corrected first entry note")), "correct-first-note")

        val secondPackage = submit(services, request, "submit-second")
        val retest = reviews.reviews(request.requestId).last()
        assertEquals(InformationRequestReviewKind.RESUBMISSION, retest.kind)
        QuarkusTransaction.requiringNew().run {
            assertEquals(InformationRequestCorrectionState.RESUBMITTED, correctionRepository.findForRequest(request.requestId).single().state)
            assertEquals(listOf(finding.id), remediationRepository.findForRequest(request.requestId).map { it.findingId })
            val carried = decisionRepository.findForReview(retest.id).filter { it.kind == InformationRequestReviewDecisionKind.CARRIED }
            assertEquals(
                setOf(reviews.item(request.requestId, secondPackage, secondNote), reviews.item(request.requestId, secondPackage, summary)),
                carried.map { it.submissionItemId }.toSet(),
            )
        }
        val retestAssigned = reviews.assign(services, request.requestId, retest.id, FIRST_CHECK, request.parties.getValue(FIRST_REVIEWER), request.owner, "assign-retest")
        val correctedItem = reviews.item(request.requestId, secondPackage, firstNote)
        reviews.finding(
            services, request.requestId, retest.id, correctedItem, InformationRequestFindingSeverity.OBSERVATION,
            InformationRequestFindingCorrectionScope.NONE, request.access(FIRST_REVIEWER), "retest-first-note",
            retests = finding.id, retestResult = InformationRequestRetestResult.RESOLVED,
        )
        val firstSettled = reviews.decide(
            services, request.requestId, retestAssigned,
            reviews.undecided(retest.id, FIRST_CHECK).associateWith { InformationRequestReviewOutcome.SATISFIED },
            request.access(FIRST_REVIEWER), "decide-retest-first-check",
        )
        assertEquals(InformationRequestState.IN_PROGRESS, firstSettled.request.state)

        val separated = assertThrows(InformationRequestLifecycleException::class.java) {
            reviews.assign(services, request.requestId, retest.id, SECOND_CHECK, request.parties.getValue(FIRST_REVIEWER), request.owner, "assign-prior-reviewer")
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, separated.reasonCode)
        val secondAssigned = reviews.assign(services, request.requestId, retest.id, SECOND_CHECK, request.parties.getValue(SECOND_REVIEWER), request.owner, "assign-second-check")
        val closed = reviews.decide(
            services, request.requestId, secondAssigned,
            reviews.undecided(retest.id, SECOND_CHECK).associateWith { InformationRequestReviewOutcome.SATISFIED },
            request.access(SECOND_REVIEWER), "decide-second-check",
        )
        assertEquals(InformationRequestReviewState.SATISFIED, closed.review.state)
        assertEquals(InformationRequestState.CLOSED, closed.request.state)
    }

    @Test
    fun `parallel stages review an unregistered respondent's package together, aggregate their findings, and settle only when every stage has decided`()
    {
        val request = published.issue(
            "parallel-review-request", listOf(CONTENT, RECORD), listOf(CONTRIBUTOR, CONTENT_REVIEWER, RECORD_REVIEWER), setOf(CONTRIBUTOR),
        ) { schemaVersionId, fields ->
            parallelConfiguration(schemaVersionId, fields.getValue(CONTENT).fieldDefinitionId, fields.getValue(RECORD).fieldDefinitionId)
        }
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val content = published.requirement(request, CONTENT).id
        val record = published.requirement(request, RECORD).id
        answer(services, request, mapOf(content to (CONTENT to "content answer"), record to (RECORD to "record answer")), "parallel-answers")
        val submitted = submit(services, request, "submit-parallel")
        dataSource.connection.use { connection ->
            assertEquals(
                "PARTICIPANT",
                queryString(connection, "SELECT submitted_by_principal_kind FROM information_request_submission_package WHERE id = ?", submitted),
            )
        }
        val review = reviews.reviews(request.requestId).single()
        val contentItem = reviews.item(request.requestId, submitted, content)
        val recordItem = reviews.item(request.requestId, submitted, record)
        assertEquals(listOf(contentItem), reviews.coverage(review.id, CONTENT_CHECK))
        assertEquals(listOf(recordItem), reviews.coverage(review.id, RECORD_CHECK))

        val contentAssigned = reviews.assign(services, request.requestId, review.id, CONTENT_CHECK, request.parties.getValue(CONTENT_REVIEWER), request.owner, "assign-content")
        val recordAssigned = reviews.assign(services, request.requestId, review.id, RECORD_CHECK, request.parties.getValue(RECORD_REVIEWER), request.owner, "assign-record")
        reviews.finding(
            services, request.requestId, review.id, contentItem, InformationRequestFindingSeverity.OBSERVATION,
            InformationRequestFindingCorrectionScope.NONE, request.access(CONTENT_REVIEWER), "content-observation",
        )
        reviews.finding(
            services, request.requestId, review.id, recordItem, InformationRequestFindingSeverity.MINOR,
            InformationRequestFindingCorrectionScope.NONE, request.access(RECORD_REVIEWER), "record-minor",
        )
        val halfway = reviews.decide(
            services, request.requestId, contentAssigned, mapOf(contentItem to InformationRequestReviewOutcome.SATISFIED),
            request.access(CONTENT_REVIEWER), "decide-content",
        )
        assertTrue(!halfway.review.state.settled, "one parallel stage does not settle the review: ${halfway.review.state}")
        val settled = reviews.decide(
            services, request.requestId, recordAssigned, mapOf(recordItem to InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION),
            request.access(RECORD_REVIEWER), "decide-record",
        )
        assertEquals(InformationRequestReviewState.SATISFIED_WITH_EXCEPTION, settled.review.state)
        assertEquals(InformationRequestState.CLOSED, settled.request.state)
        QuarkusTransaction.requiringNew().run {
            assertEquals(setOf(contentItem, recordItem), findingRepository.findForReview(review.id).map { it.submissionItemId }.toSet())
        }
    }

    private fun answer(services: InformationRequestRuntimeServices, request: PublishedRequest, values: Map<UUID, Pair<String, String>>, key: String) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = key,
                    patches = values.map { (requirementId, answer) ->
                        val (fieldKey, value) = answer
                        InformationRequestResponsePatch(
                            requirementId = requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                listOf(FieldValueEntry(request.fields.getValue(fieldKey).fieldContractId, JsonPrimitive(value))),
                                FieldsPrecondition.Unconditioned,
                            ),
                        )
                    },
                ),
            )
        }

    private fun submit(services: InformationRequestRuntimeServices, request: PublishedRequest, key: String): UUID =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(request.requestId))
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(null, contentCollector.collect(stored, null).contentHash),
                    ),
                    idempotencyKey = key,
                ),
            ).submission.submissionPackage.id
        }

    private fun sequentialConfiguration(schemaVersionId: UUID, noteField: UUID, summaryField: UUID) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        reviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL,
        reviewStages = listOf(
            InformationRequestTemplateReviewStageRequest(
                stageKey = FIRST_CHECK,
                title = "First check",
                aggregation = InformationRequestReviewAggregation.ANY,
                minimumReviewerCount = 1,
                excludesResponseParties = true,
            ),
            InformationRequestTemplateReviewStageRequest(
                stageKey = SECOND_CHECK,
                title = "Second check",
                aggregation = InformationRequestReviewAggregation.ANY,
                minimumReviewerCount = 1,
                excludesPriorReviewers = true,
            ),
        ),
        groups = listOf(InformationRequestTemplateGroupRequest(groupKey = ENTRY, maxOccurrences = 3)),
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "entries",
                title = "Entries",
                requirements = listOf(
                    reviewedField(NOTE, noteField, ENTRY),
                    reviewedField(SUMMARY, summaryField, null),
                ),
            ),
        ),
    )

    private fun parallelConfiguration(schemaVersionId: UUID, contentField: UUID, recordField: UUID) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        reviewStageOrdering = InformationRequestReviewStageOrdering.PARALLEL,
        reviewStages = listOf(
            InformationRequestTemplateReviewStageRequest(
                stageKey = CONTENT_CHECK,
                title = "Content check",
                aggregation = InformationRequestReviewAggregation.ANY,
                minimumReviewerCount = 1,
                sectionKeys = listOf("content"),
            ),
            InformationRequestTemplateReviewStageRequest(
                stageKey = RECORD_CHECK,
                title = "Record check",
                aggregation = InformationRequestReviewAggregation.ANY,
                minimumReviewerCount = 1,
                sectionKeys = listOf("records"),
            ),
        ),
        sections = listOf(
            InformationRequestTemplateSectionRequest(sectionKey = "content", title = "Content", requirements = listOf(reviewedField(CONTENT, contentField, null))),
            InformationRequestTemplateSectionRequest(sectionKey = "records", title = "Records", requirements = listOf(reviewedField(RECORD, recordField, null))),
        ),
    )

    private fun reviewedField(key: String, fieldId: UUID, anchor: String?) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.FIELD,
        prompt = "Answer $key",
        responseMode = InformationRequestResponseMode.PROVIDE,
        requiredness = InformationRequestRequiredness.REQUIRED,
        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
        reviewPolicy = InformationRequestReviewPolicy.REQUIRED,
        occurrenceAnchorKey = anchor,
        collectedFieldDefinitionId = fieldId,
        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
    )

    private companion object
    {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val FIRST_REVIEWER = "REVIEWER#first"
        const val SECOND_REVIEWER = "REVIEWER#second"
        const val CONTENT_REVIEWER = "REVIEWER#content"
        const val RECORD_REVIEWER = "REVIEWER#record"
        const val FIRST_CHECK = "first-check"
        const val SECOND_CHECK = "second-check"
        const val CONTENT_CHECK = "content-check"
        const val RECORD_CHECK = "record-check"
        const val ENTRY = "entry"
        const val NOTE = "entry-note"
        const val SUMMARY = "summary"
        const val CONTENT = "content-answer"
        const val RECORD = "record-answer"
    }
}
