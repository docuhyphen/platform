package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCapturedAttribute
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidencePolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanSettings
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanVerdict
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScannerEngine
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSignatureState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSupportingEvidenceLinkTarget
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Decision
import io.quarkus.security.ForbiddenException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InformationRequestTemplateWalkingSkeletonPhase6Test
{
    @TempDir
    lateinit var directory: Path

    private val today = LocalDate.now(Clock.systemUTC())
    private val fixture = InformationRequestEvidenceServiceFixture()
    private val requestQueries: InformationRequestQueryService = mock()
    private val uploads = fixture.uploadService()
    private val queries = fixture.queryService(requestQueries, mock<AuditRecorder>())

    init
    {
        whenever(requestQueries.findById(fixture.request.id, fixture.access)).thenReturn(fixture.request)
    }

    @Test
    fun `the basic fixture's supporting record collects independent files and is satisfied only by scanned documents`()
    {
        usePolicy(fixture.documentRequirement, basicFixture(), "supporting-record")

        uploadPdf(fixture.documentRequirement, "first-record", pages = 1)
        assertEquals(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT, state(fixture.documentRequirement))
        scanAll(InformationRequestEvidenceScanVerdict.Clean)
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, state(fixture.documentRequirement))

        val refusedType = assertThrows(InformationRequestLifecycleException::class.java)
        {
            upload(fixture.documentRequirement, file("not-a-document.pdf", "plain text".toByteArray()), "plain-text")
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED, refusedType.reasonCode)

        uploadPdf(fixture.documentRequirement, "second-record", pages = 2)
        assertEquals(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT, state(fixture.documentRequirement))
        scanAll(InformationRequestEvidenceScanVerdict.Clean)
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, state(fixture.documentRequirement))
        assertEquals(2, fixture.artifacts.size)

        val refusedCount = assertThrows(InformationRequestLifecycleException::class.java)
        {
            uploadPdf(fixture.documentRequirement, "third-record", pages = 3)
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED, refusedCount.reasonCode)
    }

    @Test
    fun `quarantined content of the basic fixture is refused to every reader and blocks its Requirement`()
    {
        usePolicy(fixture.documentRequirement, basicFixture(), "supporting-record")
        val artifact = uploadPdf(fixture.documentRequirement, "infected-record", pages = 1)
        scanAll(InformationRequestEvidenceScanVerdict.Detected("Synthetic.Test.Signature"))

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            queries.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                artifact.id,
                fixture.versions.single().id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }

        assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED, refusal.reasonCode)
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, state(fixture.documentRequirement))
    }

    @Test
    fun `the basic fixture's recorded summary is linked to the supporting record that supports it`()
    {
        val configuration = basicFixture().configuration
        val keys = configuration.sections.flatMap { it.requirements }
        val bindings = keys.associate { it.requirementKey to UUID.randomUUID() }
        val links = keys.flatMap { requirement ->
            requirement.supportingEvidenceRequirementKeys.map { supporting ->
                InformationRequestTemplateBindingEvidenceLink().apply {
                    templateBindingId = bindings.getValue(requirement.requirementKey)
                    supportingTemplateBindingId = bindings.getValue(supporting)
                    templateVersionId = fixture.request.templateVersionId
                }
            }
        }
        val summary = runtime(bindings.getValue("recorded-summary"), "root")
        val record = runtime(bindings.getValue("supporting-record"), "root")

        assertEquals(
            listOf(InformationRequestSupportingEvidenceLinkTarget(links.single().id, summary.id, record.id)),
            InformationRequestSupportingEvidenceLinkResolver.resolve(links, listOf(summary, record)),
        )
    }

    @Test
    fun `the staged fixture's primary evidence needs continuous attributed files and accepts review of a deficiency`()
    {
        usePolicy(fixture.documentRequirement, stagedFixture(), "primary-evidence-record")

        uploadPdf(fixture.documentRequirement, "first-period", pages = 2, attributes = attributed(startsDaysAgo = 60, endsDaysAgo = 40))
        val second = uploadPdf(
            fixture.documentRequirement,
            "second-period",
            pages = 3,
            attributes = attributed(startsDaysAgo = 39, endsDaysAgo = 20),
        )
        scanAll(InformationRequestEvidenceScanVerdict.Clean)
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, state(fixture.documentRequirement))

        uploads.replace(
            ReplaceInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = fixture.documentRequirement.id,
                artifactId = second.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = fixture.expectedArtifactETag(second),
                idempotencyKey = "uncertified-replacement",
                file = pdf("uncertified-period", pages = 3),
                attributes = attributed(startsDaysAgo = 39, endsDaysAgo = 20).copy(certificationReference = null),
            ),
        )
        scanAll(InformationRequestEvidenceScanVerdict.Clean)

        assertEquals(InformationRequestEvidenceRequirementState.REVIEWABLE, state(fixture.documentRequirement))
    }

    @Test
    fun `a caller who may neither view nor manage the staged fixture's primary evidence cannot list or download it`()
    {
        usePolicy(fixture.documentRequirement, stagedFixture(), "primary-evidence-record")
        val artifact = uploadPdf(fixture.documentRequirement, "restricted-record", pages = 1)
        listOf(Action.INFORMATION_REQUEST_EVIDENCE_VIEW, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE).forEach { action ->
            whenever(fixture.authorizationService.authorize(any(), eq(action), any(), any()))
                .thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))
        }

        assertThrows(ForbiddenException::class.java)
        {
            queries.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        }
        assertThrows(ForbiddenException::class.java)
        {
            queries.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                artifact.id,
                fixture.versions.single().id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
    }

    @Test
    fun `the staged fixture's waiver of primary evidence is a request awaiting review approval`()
    {
        usePolicy(fixture.documentRequirement, stagedFixture(), "primary-evidence-record")
        val response = com.docuhyphen.app.api.model.entity.InformationRequestResponse().apply {
            informationRequestId = fixture.request.id
            informationRequestRequirementId = fixture.documentRequirement.id
            disposition = InformationRequestResponseDisposition.WAIVED
        }
        whenever(fixture.responseStore.findCurrentForRequest(fixture.request.id)).thenReturn(listOf(response))

        val evaluation = queries.list(fixture.request.id, fixture.documentRequirement.id, fixture.access).evaluation

        assertEquals(InformationRequestEvidenceRequirementState.WAIVER_REQUESTED, evaluation?.state)
        assertTrue(evaluation?.state?.completesWork == true)
    }

    @Test
    fun `the staged fixture's alternate record satisfies its primary evidence as a substitute`()
    {
        usePolicy(fixture.documentRequirement, stagedFixture(), "primary-evidence-record")
        val alternate = fixture.additionalDocumentRequirement()
        usePolicy(alternate, stagedFixture(), "alternate-evidence-record")
        whenever(fixture.substituteRepository.findForBinding(fixture.documentRequirement.sourceTemplateBindingId)).thenReturn(
            listOf(
                InformationRequestTemplateBindingSubstitute().apply {
                    templateBindingId = fixture.documentRequirement.sourceTemplateBindingId
                    substituteTemplateBindingId = alternate.sourceTemplateBindingId
                    templateVersionId = fixture.request.templateVersionId
                },
            ),
        )

        uploadPdf(alternate, "alternate-record", pages = 1)
        scanAll(InformationRequestEvidenceScanVerdict.Clean)

        val evaluation = queries.list(fixture.request.id, fixture.documentRequirement.id, fixture.access).evaluation
        assertEquals(InformationRequestEvidenceRequirementState.SATISFIED, evaluation?.state)
        assertTrue(evaluation?.satisfiedBySubstitute == true)
    }

    @Test
    fun `each reported item of the staged fixture links its subject status to the primary evidence at the root`()
    {
        val configuration = stagedFixture().configuration
        val keys = configuration.sections.flatMap { it.requirements }
        val bindings = keys.associate { it.requirementKey to UUID.randomUUID() }
        val link = InformationRequestTemplateBindingEvidenceLink().apply {
            templateBindingId = bindings.getValue("subject-status")
            supportingTemplateBindingId = bindings.getValue(keys.single { it.requirementKey == "subject-status" }
                .supportingEvidenceRequirementKeys.single())
            templateVersionId = fixture.request.templateVersionId
        }
        val firstItem = runtime(bindings.getValue("subject-status"), "reported-item[0]")
        val secondItem = runtime(bindings.getValue("subject-status"), "reported-item[1]")
        val primary = runtime(bindings.getValue("primary-evidence-record"), "root")

        assertEquals(
            listOf(firstItem.id to primary.id, secondItem.id to primary.id),
            InformationRequestSupportingEvidenceLinkResolver.resolve(listOf(link), listOf(firstItem, secondItem, primary))
                .map { it.supportedRequirementId to it.supportingRequirementId },
        )
    }

    private fun basicFixture() =
        InformationRequestTemplateWalkingSkeletonFixtures.basicFieldDocumentResponseAttestationRequest(UUID.randomUUID(), UUID.randomUUID())

    private fun stagedFixture() =
        InformationRequestTemplateWalkingSkeletonFixtures.multiPartyStagedEvidenceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

    private fun usePolicy(
        requirement: InformationRequestRequirement,
        walkingFixture: InformationRequestTemplateWalkingSkeletonFixture,
        requirementKey: String,
    )
    {
        val request = walkingFixture.configuration.sections.flatMap { it.requirements }
            .single { it.requirementKey == requirementKey }
            .evidencePolicy!!
        whenever(fixture.policyLoader.forBinding(requirement.sourceTemplateBindingId)).thenReturn(policyOf(request))
    }

    private fun policyOf(request: InformationRequestTemplateEvidencePolicyRequest) = InformationRequestEvidencePolicy(
        minimumFileCount = request.minimumFileCount,
        maximumFileCount = request.maximumFileCount,
        maximumFileSizeBytes = request.maximumFileSizeBytes,
        maximumTotalSizeBytes = request.maximumTotalSizeBytes,
        minimumPageCount = request.minimumPageCount,
        maximumPageCount = request.maximumPageCount,
        attributeRequirements = mapOf(
            InformationRequestEvidenceCapturedAttribute.ISSUER to request.issuerRequirement,
            InformationRequestEvidenceCapturedAttribute.JURISDICTION to request.jurisdictionRequirement,
            InformationRequestEvidenceCapturedAttribute.LANGUAGE to request.languageRequirement,
            InformationRequestEvidenceCapturedAttribute.ISSUE_DATE to request.issueDateRequirement,
            InformationRequestEvidenceCapturedAttribute.EXPIRY_DATE to request.expiryDateRequirement,
            InformationRequestEvidenceCapturedAttribute.COVERAGE_PERIOD to request.coveragePeriodRequirement,
            InformationRequestEvidenceCapturedAttribute.CERTIFICATION to request.certificationRequirement,
            InformationRequestEvidenceCapturedAttribute.SIGNATURE to request.signatureRequirement,
        ),
        acceptedValues = request.acceptedValues.groupBy({ it.attribute }, { it.acceptedValue }).mapValues { it.value.toSet() },
        maximumIssueAgeDays = request.maximumIssueAgeDays,
        minimumRemainingValidityDays = request.minimumRemainingValidityDays,
        minimumCoverageDays = request.minimumCoverageDays,
        coverageContinuityRequired = request.coverageContinuityRequired,
        waiverPolicy = request.waiverPolicy,
        conformancePolicy = request.conformancePolicy,
    )

    private fun attributed(startsDaysAgo: Long, endsDaysAgo: Long) = InformationRequestEvidenceAttributes(
        issuer = "recording-party",
        language = "process-language",
        issuedOn = today.minusDays(10),
        expiresOn = today.plusDays(100),
        coverage = InformationRequestEvidenceCoverage(today.minusDays(startsDaysAgo), today.minusDays(endsDaysAgo)),
        certificationReference = "CERT-1",
    )

    private fun state(requirement: InformationRequestRequirement): InformationRequestEvidenceRequirementState? =
        queries.list(fixture.request.id, requirement.id, fixture.access).evaluation?.state

    private fun scanAll(verdict: InformationRequestEvidenceScanVerdict)
    {
        val scanner = object : InformationRequestEvidenceMalwareScanner
        {
            override fun engine() = InformationRequestEvidenceScannerEngine("process-scanner", "1.0", productionEligible = true)
            override fun signatures() = InformationRequestEvidenceSignatureState("2026.09.25", Instant.now().minusSeconds(60))
            override fun scan(content: File) = verdict
        }
        val service = InformationRequestEvidenceMalwareAssessmentService(
            versionRepository = fixture.versionRepository,
            documentVersionRecordingService = fixture.documentVersionRecordingService,
            assessmentRepository = fixture.assessmentRepository,
            scanner = scanner,
            settings = InformationRequestEvidenceScanSettings(Duration.ofSeconds(5), Duration.ofDays(1), Duration.ofDays(30)),
            clock = Clock.systemUTC(),
            scanAudit = fixture.scanAudit,
        )
        fixture.versions
            .filter { version -> fixture.assessments.none { it.evidenceVersionId == version.id && it.outcome in SETTLED } }
            .forEach { service.assess(it.id) }
    }

    private fun uploadPdf(
        requirement: InformationRequestRequirement,
        key: String,
        pages: Int,
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
    ): InformationRequestEvidenceArtifact = upload(requirement, pdf(key, pages), key, attributes)

    private fun upload(
        requirement: InformationRequestRequirement,
        file: InformationRequestEvidenceFile,
        key: String,
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
    ): InformationRequestEvidenceArtifact =
        uploads.upload(
            UploadInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = requirement.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = fixture.expectedEvidenceETag(requirement),
                idempotencyKey = key,
                file = file,
                attributes = attributes,
            ),
        ).artifact.artifact

    private fun pdf(key: String, pages: Int): InformationRequestEvidenceFile
    {
        val target = directory.resolve("$key.pdf").toFile()
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.documentInformation.title = key
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "$key.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun file(name: String, bytes: ByteArray) =
        InformationRequestEvidenceFile(
            directory.resolve(name).toFile().apply { writeBytes(bytes) },
            name,
            "application/pdf",
            DocumentEncryptionMode.INTERNAL,
        )

    private fun runtime(bindingId: UUID, path: String) = InformationRequestRequirement().apply {
        informationRequestId = fixture.request.id
        sourceTemplateVersionId = fixture.request.templateVersionId
        sourceTemplateRequirementId = UUID.randomUUID()
        sourceTemplateBindingId = bindingId
        occurrencePath = path
    }

    private companion object
    {
        val SETTLED = setOf("CLEAN", "MALWARE_DETECTED")
    }
}
