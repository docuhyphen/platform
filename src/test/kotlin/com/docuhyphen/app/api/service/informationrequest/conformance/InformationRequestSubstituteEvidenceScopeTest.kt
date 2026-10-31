package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionContent
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.evidence.*
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionIncompleteException
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubstituteEvidenceScopeTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository

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
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var evidenceVersionRepository: InformationRequestEvidenceVersionRepository

    @Inject
    lateinit var submissionEvidenceRepository: InformationRequestSubmissionEvidenceRepository

    @Inject
    lateinit var documentVersionRecordingService: DocumentVersionRecordingService

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Inject
    lateinit var intake: InformationRequestEvidenceIntake

    @Inject
    lateinit var evaluationService: InformationRequestEvidenceEvaluationService

    @Inject
    lateinit var lockService: InformationRequestSubmissionLockService

    @Inject
    lateinit var executionGrants: InformationRequestExecutionGrantService

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @Inject
    lateinit var contentCollector: InformationRequestSubmissionContentCollector

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

    @Test
    fun `an alternative hidden by its condition no longer completes the required document`() {
        val request = hiddenAlternative()

        val evaluation = evaluate(published.requirement(request, PRIMARY).id)

        assertEquals(InformationRequestEvidenceRequirementState.NOT_PROVIDED, evaluation.state)
        assertFalse(evaluation.satisfiedBySubstitute)
    }

    @Test
    fun `a whole package refuses a required document whose only alternative is hidden and keeps the hidden files out`() {
        val request = hiddenAlternative()

        assertNull(content(request, null).items.single { it.requirementKey == ALTERNATE }.evidence)
        assertThrows(InformationRequestSubmissionIncompleteException::class.java) {
            submit(request, null, "submit-hidden-alternative")
        }
    }

    @Test
    fun `an alternative shown again completes the required document with its exact retained version`() {
        val request = hiddenAlternative()
        val retainedVersion = currentVersion(published.requirement(request, ALTERNATE).id)
        provide(request, published.requirement(request, SELECTION).id, "show-alternative-again")

        assertEquals(
            InformationRequestEvidenceRequirementState.SATISFIED,
            evaluate(published.requirement(request, PRIMARY).id).state
        )
        val packageId = submit(request, null, "submit-shown-alternative")
        assertEquals(setOf(retainedVersion), members(packageId))
    }

    @Test
    fun `an active alternative is frozen with the whole package it completes`() {
        val request = issue(wholeConfiguration(conditional = false))
        val alternate = published.requirement(request, ALTERNATE)
        val uploaded = upload(request, alternate.id, "\"${alternate.id}:0\"", "whole-alternative")

        val packageId = submit(request, null, "submit-whole-alternative")

        assertEquals(setOf(uploaded.artifact.versions.single().version.id), members(packageId))
    }

    @Test
    fun `an absent alternative leaves the required document incomplete`() {
        val request = issue(wholeConfiguration(conditional = false))

        assertEquals(
            InformationRequestEvidenceRequirementState.NOT_PROVIDED,
            evaluate(published.requirement(request, PRIMARY).id).state
        )
        assertThrows(InformationRequestSubmissionIncompleteException::class.java) {
            submit(request, null, "submit-absent-alternative")
        }
    }

    @Test
    fun `a staged Template cannot let a document be completed by an alternative answered in another stage`() {
        val refused = assertThrows(InformationRequestTemplateValidationException::class.java) {
            issue(stagedConfiguration(alternateStage = LATER_STAGE))
        }

        assertEquals(PRIMARY, refused.requirementKey)
        assertTrue(refused.message.contains(LATER_STAGE), refused.message)
    }

    @Test
    fun `an alternative answered in its own stage changes that stage's reviewed content and is frozen with it`() {
        val request = issue(stagedConfiguration(alternateStage = FIRST_STAGE))
        val alternate = published.requirement(request, ALTERNATE)
        val first = upload(request, alternate.id, "\"${alternate.id}:0\"", "first-stage-alternative")
        val before = content(request, FIRST_STAGE).contentHash
        assertEquals(
            InformationRequestEvidenceRequirementState.SATISFIED,
            evaluate(published.requirement(request, PRIMARY).id).state
        )

        val replaced = replace(request, alternate.id, first, "replacement-stage-alternative")

        assertNotEquals(before, content(request, FIRST_STAGE).contentHash)
        val packageId = submit(request, FIRST_STAGE, "submit-first-stage")
        assertEquals(
            setOf(replaced.artifact.versions.maxBy { it.version.versionNumber }.version.id),
            members(packageId)
        )
    }

    private fun hiddenAlternative(): PublishedRequest {
        val request = issue(wholeConfiguration(conditional = true))
        val selection = published.requirement(request, SELECTION).id
        val alternate = published.requirement(request, ALTERNATE)
        provide(request, selection, "enable-alternative")
        upload(request, alternate.id, "\"${alternate.id}:0\"", "retained-alternative")
        assertEquals(
            InformationRequestEvidenceRequirementState.SATISFIED,
            evaluate(published.requirement(request, PRIMARY).id).state
        )
        patch(
            request, InformationRequestResponsePatch(
                requirementId = selection,
                disposition = InformationRequestResponseDisposition.UNAVAILABLE,
                narrative = ResponseNarrativePatch.Set("The alternative no longer applies"),
            ), "hide-alternative"
        )
        return request
    }

    private fun issue(configuration: InformationRequestTemplateConfigurationRequest): PublishedRequest =
        published.issue("substitute-evidence-scope", emptyList(), listOf(CONTRIBUTOR)) { _, _ -> configuration }

    private fun wholeConfiguration(conditional: Boolean) = InformationRequestTemplateConfigurationRequest(
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "records",
                title = "Records",
                requirements = listOf(
                    primary(),
                    alternate(conditional)
                ) + if (conditional) listOf(selection()) else emptyList(),
            ),
        ),
        conditionRules = if (conditional) listOf(
            InformationRequestTemplateConditionRuleRequest(
                ruleKey = ALTERNATIVE_RULE,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateRequest(
                        sourceRequirementKey = SELECTION,
                        operator = FieldOperator.EQUALS,
                        expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
            ),
        ) else emptyList(),
    )

    private fun stagedConfiguration(alternateStage: String) = InformationRequestTemplateConfigurationRequest(
        submissionMode = InformationRequestSubmissionMode.STAGED,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "first-records",
                title = "First records",
                requirements = listOf(primary()) + if (alternateStage == FIRST_STAGE) listOf(alternate(false)) else emptyList(),
                submissionStageKey = FIRST_STAGE,
            ),
            InformationRequestTemplateSectionRequest(
                sectionKey = "later-records",
                title = "Later records",
                requirements = listOf(laterRecord()) + if (alternateStage == LATER_STAGE) listOf(alternate(false)) else emptyList(),
                submissionStageKey = LATER_STAGE,
            ),
        ),
    )

    private fun primary() = document(PRIMARY, "Provide the period records", InformationRequestRequiredness.REQUIRED)
        .copy(substituteRequirementKeys = listOf(ALTERNATE))

    private fun alternate(conditional: Boolean) =
        document(
            ALTERNATE,
            "Provide an alternate record",
            if (conditional) InformationRequestRequiredness.CONDITIONAL else InformationRequestRequiredness.OPTIONAL,
        ).copy(conditionalRuleKey = if (conditional) ALTERNATIVE_RULE else null)

    private fun selection() =
        document(SELECTION, "Choose whether an alternate record applies", InformationRequestRequiredness.OPTIONAL)
            .copy(
                permittedDispositions = listOf(
                    InformationRequestResponseDisposition.PROVIDED,
                    InformationRequestResponseDisposition.UNAVAILABLE
                )
            )

    private fun laterRecord() =
        document(LATER_RECORD, "Provide the closing record", InformationRequestRequiredness.OPTIONAL)

    private fun document(key: String, prompt: String, requiredness: InformationRequestRequiredness) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = InformationRequestRequirementType.DOCUMENT,
            prompt = prompt,
            requiredness = requiredness,
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                minimumFileCount = 1,
                maximumFileCount = 2
            ),
        )

    private fun upload(
        request: PublishedRequest,
        requirementId: UUID,
        etag: String,
        key: String
    ): InformationRequestEvidenceCommandResult =
        QuarkusTransaction.requiringNew().call {
            uploads().upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = request.requestId,
                    requirementId = requirementId,
                    access = request.access(CONTRIBUTOR),
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = key,
                    file = pdf(key),
                    attributes = InformationRequestEvidenceAttributes.NONE,
                ),
            )
        }

    private fun replace(
        request: PublishedRequest,
        requirementId: UUID,
        current: InformationRequestEvidenceCommandResult,
        key: String,
    ): InformationRequestEvidenceCommandResult =
        QuarkusTransaction.requiringNew().call {
            uploads().replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = request.requestId,
                    requirementId = requirementId,
                    artifactId = current.artifact.artifact.id,
                    access = request.access(CONTRIBUTOR),
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(current.artifactETag),
                    idempotencyKey = key,
                    file = pdf(key),
                    attributes = InformationRequestEvidenceAttributes.NONE,
                ),
            )
        }

    private fun provide(request: PublishedRequest, requirementId: UUID, key: String) =
        patch(
            request,
            InformationRequestResponsePatch(
                requirementId = requirementId,
                disposition = InformationRequestResponseDisposition.PROVIDED
            ),
            key
        )

    private fun patch(request: PublishedRequest, patch: InformationRequestResponsePatch, key: String) =
        QuarkusTransaction.requiringNew().call {
            runtime.build(request.requestId, centralAuthorization = authorizationService).responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = key,
                    patches = listOf(patch),
                ),
            )
        }

    private fun content(request: PublishedRequest, stageKey: String?): InformationRequestSubmissionContent =
        QuarkusTransaction.requiringNew().call {
            contentCollector.collect(requireNotNull(requestRepository.findById(request.requestId)), stageKey)
        }

    private fun submit(request: PublishedRequest, stageKey: String?, key: String): UUID =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(request.requestId))
            runtime.build(request.requestId, centralAuthorization = authorizationService).submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    stageKey = stageKey,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(
                            stageKey,
                            contentCollector.collect(stored, stageKey).contentHash
                        ),
                    ),
                    idempotencyKey = key,
                ),
            ).submission.submissionPackage.id
        }

    private fun members(packageId: UUID): Set<UUID> =
        QuarkusTransaction.requiringNew().call {
            submissionEvidenceRepository.findForPackages(listOf(packageId)).map { it.evidenceVersionId }.toSet()
        }

    private fun currentVersion(requirementId: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            artifactRepository.findForRequirement(requirementId)
                .flatMap { evidenceVersionRepository.findForArtifact(it.id) }
                .maxBy { it.versionNumber }
                .id
        }

    private fun evaluate(requirementId: UUID): InformationRequestEvidenceRequirementEvaluation =
        QuarkusTransaction.requiringNew().call {
            requireNotNull(evaluationService.evaluate(requireNotNull(requirementRepository.findById(requirementId))))
        }

    private fun pdf(title: String): InformationRequestEvidenceFile {
        val target = File.createTempFile("substitute-scope-evidence", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.documentInformation.title = "$title-${UUID.randomUUID()}"
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "$title.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun uploads(): InformationRequestEvidenceUploadService {
        val gate = InformationRequestEvidenceGate(
            requestRepository = requestRepository,
            exchangeLookup = exchangeLookup,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            templateRequirementRepository = templateRequirementRepository,
            authorizationService = authorizationService,
            continuationGuard = InformationRequestContinuationGuard(executionGrants, org.mockito.kotlin.mock()),
            lockService = lockService,
            requirementContext = org.mockito.kotlin.mock(),
            conditionVisibility = InformationRequestConditionVisibility(
                org.mockito.kotlin.mock(),
                org.mockito.kotlin.mock()
            ),
        )
        return InformationRequestEvidenceUploadService(
            gate,
            artifactRepository,
            evidenceVersionRepository,
            documentVersionRecordingService,
            commandReceiptService,
            transitionHistory,
            InformationRequestEvidenceViewLoader(evidenceVersionRepository, documentVersionRecordingService),
            intake,
            InformationRequestNoAuthAccessDoubles.current(),
        )
    }

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PRIMARY = "period-records"
        const val ALTERNATE = "alternate-record"
        const val SELECTION = "alternate-selection"
        const val LATER_RECORD = "closing-record"
        const val ALTERNATIVE_RULE = "alternate-applies"
        const val FIRST_STAGE = "first"
        const val LATER_STAGE = "later"
    }
}
