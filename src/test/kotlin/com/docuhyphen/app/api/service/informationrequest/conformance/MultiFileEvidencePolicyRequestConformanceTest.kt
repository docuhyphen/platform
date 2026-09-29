package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFindingCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanSettings
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanVerdict
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScannerEngine
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSignatureState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceContentRelease
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceEvaluationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceIntake
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceMalwareAssessmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceMalwareScanner
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceScanAudit
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceViewLoader
import com.docuhyphen.app.api.service.informationrequest.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponsePatch
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.informationrequest.ResponseNarrativePatch
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class MultiFileEvidencePolicyRequestConformanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var exchangeRepository: ExchangeRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository
    @Inject lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var materializer: InformationRequestTemplateMaterializer
    @Inject lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository
    @Inject lateinit var evidenceVersionRepository: InformationRequestEvidenceVersionRepository
    @Inject lateinit var assessmentRepository: InformationRequestEvidenceAssessmentRepository
    @Inject lateinit var submissionEvidenceRepository: InformationRequestSubmissionEvidenceRepository
    @Inject lateinit var documentVersionRecordingService: DocumentVersionRecordingService
    @Inject lateinit var commandReceiptService: CommandReceiptService
    @Inject lateinit var intake: InformationRequestEvidenceIntake
    @Inject lateinit var evaluationService: InformationRequestEvidenceEvaluationService
    @Inject lateinit var contentRelease: InformationRequestEvidenceContentRelease
    @Inject lateinit var lockService: InformationRequestSubmissionLockService
    @Inject lateinit var executionGrants: InformationRequestExecutionGrantService
    @Inject lateinit var transitionHistory: InformationRequestTransitionHistoryService
    @Inject lateinit var contentCollector: InformationRequestSubmissionContentCollector
    @Inject lateinit var scanAudit: InformationRequestEvidenceScanAudit
    @Inject lateinit var clock: Clock
    @Inject lateinit var authorizationService: AuthorizationService
    @Inject lateinit var responseRepository: com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository

    private val published by lazy {
        PublishedRequestSupport(
            dataSource, requestRepository, requirementRepository, definitionRepository, versionRepository, capabilityRepository,
            configurationWriter, materializer,
        )
    }

    @Test
    fun `several files aggregate by policy, a replacement keeps immutable membership, and quarantined content never satisfies`()
    {
        val request = issue()
        val primary = published.requirement(request, PRIMARY)
        val evidence = evidenceServices()
        val today = LocalDate.now()

        val first = upload(evidence, request, primary.id, "\"${primary.id}:0\"", "first-file", 1, good(today.minusDays(60), today.minusDays(31)))
        assertFalse(evaluate(primary.id).state.completesWork)
        assertTrue(InformationRequestEvidenceFindingCode.FILE_COUNT_BELOW_MINIMUM in codes(evaluate(primary.id)))
        val second = upload(evidence, request, primary.id, first.evidenceETag, "second-file", 2, good(today.minusDays(30), today))
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluate(primary.id).state)

        mapOf(
            "unaccepted-issuer" to (good(today.minusDays(30), today).copy(issuer = "unlisted-party") to InformationRequestEvidenceFindingCode.ATTRIBUTE_NOT_ACCEPTED),
            "stale-issue" to (good(today.minusDays(30), today).copy(issuedOn = today.minusDays(400)) to InformationRequestEvidenceFindingCode.ISSUE_TOO_OLD),
            "short-validity" to (good(today.minusDays(30), today).copy(expiresOn = today.plusDays(3)) to InformationRequestEvidenceFindingCode.VALIDITY_TOO_SHORT),
            "uncertified" to (good(today.minusDays(30), today).copy(certificationReference = null) to InformationRequestEvidenceFindingCode.ATTRIBUTE_MISSING),
            "coverage-gap" to (good(today.minusDays(20), today) to InformationRequestEvidenceFindingCode.COVERAGE_NOT_CONTINUOUS),
        ).entries.foldIndexed(second) { index, current, (key, case) ->
            val (attributes, finding) = case
            val replaced = replace(evidence, request, primary.id, current, "replace-$key", 10 + index, attributes)
            val evaluation = evaluate(primary.id)
            assertFalse(evaluation.state.completesWork, "$key leaves the requirement incomplete")
            assertTrue(finding in codes(evaluation), "$key reports $finding: ${codes(evaluation)}")
            replaced
        }.let { current ->
            replace(evidence, request, primary.id, current, "replace-restored", 20, good(today.minusDays(30), today))
        }
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluate(primary.id).state)

        val suspicious = upload(evidence, request, primary.id, currentEvidenceETag(primary.id), "suspicious-file", 30, good(today.minusDays(60), today))
        val suspiciousVersion = suspicious.artifact.versions.single().version
        QuarkusTransaction.requiringNew().run { flaggingScan().assess(suspiciousVersion.id) }
        val quarantined = evaluate(primary.id).versions.single { it.versionId == suspiciousVersion.id }
        assertEquals(InformationRequestEvidenceConformance.QUARANTINED, quarantined.conformance)
        assertTrue(quarantined.findings.any { it.code == InformationRequestEvidenceFindingCode.MALWARE_DETECTED && it.code.blocking })
        assertThrows(Exception::class.java) {
            QuarkusTransaction.requiringNew().run {
                contentRelease.requireReleasable(requireNotNull(evidenceVersionRepository.findById(suspiciousVersion.id)), request.owner)
            }
        }
        QuarkusTransaction.requiringNew().call {
            evidence.collection.withdraw(
                ChangeInformationRequestEvidenceStateCommand(
                    requestId = request.requestId,
                    requirementId = primary.id,
                    artifactId = suspicious.artifact.artifact.id,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(suspicious.artifactETag),
                    idempotencyKey = "withdraw-suspicious",
                    reason = "Replaced by a clean file",
                ),
            )
        }
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluate(primary.id).state)

        provide(request, primary.id, "provide-primary")
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val firstPackage = submit(services, request, "submit-first")
        val firstMembers = members(firstPackage)
        QuarkusTransaction.requiringNew().call {
            services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = request.requestId,
                    packageId = firstPackage,
                    reasonCode = "record-refresh",
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = "withdraw-first",
                ),
            )
        }
        val refreshedArtifact = currentArtifact(first.artifact.artifact.id)
        replace(evidence, request, primary.id, refreshedArtifact, "refresh-first", 40, good(today.minusDays(60), today.minusDays(31)))
        val secondPackage = submit(services, request, "submit-second")

        assertEquals(firstMembers, members(firstPackage), "a submitted package keeps its exact evidence versions")
        assertFalse(members(secondPackage) == firstMembers)
        val firstVersionOfFirstFile = first.artifact.versions.single().version.id
        assertTrue(firstVersionOfFirstFile in firstMembers)
        assertFalse(firstVersionOfFirstFile in members(secondPackage))
        assertEquals(
            InformationRequestEvidenceStanding.SUPERSEDED,
            evaluate(primary.id).versions.single { it.versionId == firstVersionOfFirstFile }.standing,
        )
    }

    @Test
    fun `a configured alternative satisfies the primary evidence and a waiver awaits review approval`()
    {
        val substituted = issue()
        val primary = published.requirement(substituted, PRIMARY)
        val alternate = published.requirement(substituted, ALTERNATE)
        val evidence = evidenceServices()
        val today = LocalDate.now()

        upload(evidence, substituted, alternate.id, "\"${alternate.id}:0\"", "alternate-file", 50, InformationRequestEvidenceAttributes.NONE)
        val bySubstitute = evaluate(primary.id)
        assertTrue(bySubstitute.satisfiedBySubstitute)
        assertTrue(bySubstitute.state.completesWork)

        val waived = issue()
        val waivedPrimary = published.requirement(waived, PRIMARY)
        upload(evidence, waived, waivedPrimary.id, "\"${waivedPrimary.id}:0\"", "only-file", 60, good(today.minusDays(60), today))
        QuarkusTransaction.requiringNew().call {
            runtime.build(waived.requestId, centralAuthorization = authorizationService).responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = waived.requestId,
                    access = waived.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(waived)),
                    idempotencyKey = "request-waiver",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = waivedPrimary.id,
                            disposition = InformationRequestResponseDisposition.WAIVED,
                            narrative = ResponseNarrativePatch.Set("The second record is not issued for this period"),
                        ),
                    ),
                ),
            )
        }
        val storedDisposition = QuarkusTransaction.requiringNew().call {
            responseRepository.findCurrentForRequest(waived.requestId).single { it.informationRequestRequirementId == waivedPrimary.id }.disposition
        }
        assertEquals(InformationRequestResponseDisposition.WAIVED, storedDisposition)
        assertEquals(InformationRequestEvidenceRequirementState.WAIVER_REQUESTED, evaluate(waivedPrimary.id, storedDisposition).state)
        val waivedProblems = QuarkusTransaction.requiringNew().call {
            runtime.build(waived.requestId, centralAuthorization = authorizationService).submissionQueries
                .preview(waived.requestId, null, waived.access(CONTRIBUTOR)).readiness.problems
        }
        assertTrue(waivedProblems.none { it.requirementId == waivedPrimary.id }, waivedProblems.toString())
    }

    private fun issue(): PublishedRequest =
        published.issue("multi-file-evidence-policy-request", emptyList(), listOf(CONTRIBUTOR)) { _, _ -> configuration() }

    private fun good(coverageStart: LocalDate, coverageEnd: LocalDate) = InformationRequestEvidenceAttributes(
        issuer = ISSUER,
        issuedOn = LocalDate.now().minusDays(10),
        expiresOn = LocalDate.now().plusDays(200),
        coverage = InformationRequestEvidenceCoverage(coverageStart, coverageEnd),
        certificationReference = "certification-reference",
    )

    private fun upload(
        services: EvidenceServices,
        request: PublishedRequest,
        requirementId: UUID,
        etag: String,
        key: String,
        pages: Int,
        attributes: InformationRequestEvidenceAttributes,
    ): InformationRequestEvidenceCommandResult =
        QuarkusTransaction.requiringNew().call {
            services.uploads.upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = request.requestId,
                    requirementId = requirementId,
                    access = request.access(CONTRIBUTOR),
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = key,
                    file = pdf(pages, key),
                    attributes = attributes,
                ),
            )
        }

    private fun replace(
        services: EvidenceServices,
        request: PublishedRequest,
        requirementId: UUID,
        current: InformationRequestEvidenceCommandResult,
        key: String,
        pages: Int,
        attributes: InformationRequestEvidenceAttributes,
    ): InformationRequestEvidenceCommandResult =
        QuarkusTransaction.requiringNew().call {
            services.uploads.replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = request.requestId,
                    requirementId = requirementId,
                    artifactId = current.artifact.artifact.id,
                    access = request.access(CONTRIBUTOR),
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(current.artifactETag),
                    idempotencyKey = key,
                    file = pdf(pages, key),
                    attributes = attributes,
                ),
            )
        }

    private fun currentArtifact(artifactId: UUID): InformationRequestEvidenceCommandResult =
        QuarkusTransaction.requiringNew().call {
            val artifact = requireNotNull(artifactRepository.findById(artifactId))
            InformationRequestEvidenceCommandResult(
                artifact = com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceArtifactView(artifact, emptyList()),
                evidenceETag = "",
                artifactETag = InformationRequestETag.artifactOf(artifact),
            )
        }

    private fun currentEvidenceETag(requirementId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.evidenceOf(requirementId, artifactRepository.findForRequirement(requirementId))
        }

    private fun provide(request: PublishedRequest, requirementId: UUID, key: String) =
        QuarkusTransaction.requiringNew().call {
            runtime.build(request.requestId, centralAuthorization = authorizationService).responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = request.access(CONTRIBUTOR),
                    precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
                    idempotencyKey = key,
                    patches = listOf(InformationRequestResponsePatch(requirementId = requirementId, disposition = InformationRequestResponseDisposition.PROVIDED)),
                ),
            )
        }

    private fun submit(services: com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices, request: PublishedRequest, key: String): UUID =
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

    private fun members(packageId: UUID): Set<UUID> =
        QuarkusTransaction.requiringNew().call { submissionEvidenceRepository.findForPackages(listOf(packageId)).map { it.evidenceVersionId }.toSet() }

    private fun evaluate(requirementId: UUID, disposition: InformationRequestResponseDisposition? = null): InformationRequestEvidenceRequirementEvaluation =
        QuarkusTransaction.requiringNew().call {
            requireNotNull(evaluationService.evaluate(requireNotNull(requirementRepository.findById(requirementId)), disposition))
        }

    private fun codes(evaluation: InformationRequestEvidenceRequirementEvaluation): Set<InformationRequestEvidenceFindingCode> =
        (evaluation.findings + evaluation.versions.filter { it.standing == InformationRequestEvidenceStanding.CURRENT }.flatMap { it.findings })
            .map { it.code }
            .toSet()

    private fun pdf(pages: Int, title: String): InformationRequestEvidenceFile
    {
        val target = File.createTempFile("conformance-evidence", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.documentInformation.title = "$title-${UUID.randomUUID()}"
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "$title.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun flaggingScan(): InformationRequestEvidenceMalwareAssessmentService =
        InformationRequestEvidenceMalwareAssessmentService(
            evidenceVersionRepository,
            documentVersionRecordingService,
            assessmentRepository,
            FlaggingScanner,
            InformationRequestEvidenceScanSettings(Duration.ofSeconds(30), Duration.ofDays(1), Duration.ofMinutes(5)),
            clock,
            scanAudit,
        )

    private object FlaggingScanner : InformationRequestEvidenceMalwareScanner
    {
        override fun engine() = InformationRequestEvidenceScannerEngine("conformance-test-scanner", "1", productionEligible = true)

        override fun signatures() = InformationRequestEvidenceSignatureState("1", Instant.now())

        override fun scan(content: File): InformationRequestEvidenceScanVerdict = InformationRequestEvidenceScanVerdict.Detected("test-signature")
    }

    private fun evidenceServices(): EvidenceServices
    {
        val gate = InformationRequestEvidenceGate(
            requestRepository = requestRepository,
            exchangeRepository = exchangeRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            templateRequirementRepository = templateRequirementRepository,
            authorizationService = authorizationService,
            entitlementGuard = org.mockito.kotlin.mock(),
            executionGrantService = executionGrants,
            lockService = lockService,
        )
        val viewLoader = InformationRequestEvidenceViewLoader(evidenceVersionRepository, documentVersionRecordingService)
        return EvidenceServices(
            uploads = InformationRequestEvidenceUploadService(
                gate, artifactRepository, evidenceVersionRepository, documentVersionRecordingService, commandReceiptService,
                transitionHistory, viewLoader, intake,
            ),
            collection = InformationRequestEvidenceCollectionService(gate, artifactRepository, commandReceiptService, transitionHistory, viewLoader),
        )
    }

    private fun configuration() = InformationRequestTemplateConfigurationRequest(
        reviewStages = listOf(
            InformationRequestTemplateReviewStageRequest(
                stageKey = "record-review",
                title = "Record review",
                aggregation = InformationRequestReviewAggregation.ANY,
                minimumReviewerCount = 1,
            ),
        ),
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "records",
                title = "Records",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = PRIMARY,
                        requirementType = InformationRequestRequirementType.DOCUMENT,
                        prompt = "Provide the period records",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        reviewPolicy = InformationRequestReviewPolicy.REQUIRED,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.WAIVED),
                        substituteRequirementKeys = listOf(ALTERNATE),
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            minimumFileCount = 2,
                            maximumFileCount = 5,
                            maximumFileSizeBytes = 2_000_000,
                            maximumTotalSizeBytes = 8_000_000,
                            issuerRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
                            issueDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
                            expiryDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
                            coveragePeriodRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
                            certificationRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
                            maximumIssueAgeDays = 120,
                            minimumRemainingValidityDays = 15,
                            minimumCoverageDays = 45,
                            coverageContinuityRequired = true,
                            waiverPolicy = InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED,
                            acceptedValues = listOf(
                                InformationRequestTemplateAcceptedValueRequest(InformationRequestEvidenceAttribute.CONTENT_TYPE, "application/pdf"),
                                InformationRequestTemplateAcceptedValueRequest(InformationRequestEvidenceAttribute.ISSUER, ISSUER),
                            ),
                        ),
                    ),
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = ALTERNATE,
                        requirementType = InformationRequestRequirementType.DOCUMENT,
                        prompt = "Provide an alternate record",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.OPTIONAL,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            minimumFileCount = 1,
                            maximumFileCount = 1,
                            maximumFileSizeBytes = 2_000_000,
                            maximumTotalSizeBytes = 2_000_000,
                            acceptedValues = listOf(
                                InformationRequestTemplateAcceptedValueRequest(InformationRequestEvidenceAttribute.CONTENT_TYPE, "application/pdf"),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private class EvidenceServices(
        val uploads: InformationRequestEvidenceUploadService,
        val collection: InformationRequestEvidenceCollectionService,
    )

    private companion object
    {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val PRIMARY = "period-records"
        const val ALTERNATE = "alternate-record"
        const val ISSUER = "recording-party"
    }
}
