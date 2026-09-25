package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDocumentVersionEvidenceSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCapturedAttribute
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidencePolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceStoredUsage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceUploadLimits
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionSourceMapper
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InformationRequestEvidenceIntakeTest
{
    @TempDir
    lateinit var directory: Path

    private val fixture = InformationRequestEvidenceServiceFixture()
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy = mock()
    private val policyLoader: InformationRequestEvidencePolicyLoader = mock()
    private val artifactRepository: InformationRequestEvidenceArtifactRepository = mock()
    private val versionRepository: InformationRequestEvidenceVersionRepository = mock()
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository = mock()
    private val recordingService: DocumentVersionRecordingService = mock()
    private val limits = InformationRequestEvidenceUploadLimits(
        maximumFileBytes = 1_000,
        maximumNoAuthFileBytes = 500,
        maximumRequestFiles = 3,
        maximumRequestBytes = 3_000,
        maximumPartyFiles = 2,
        maximumPartyBytes = 2_000,
    )

    init
    {
        whenever(versionRepository.storedUsageForRequest(any())).thenReturn(InformationRequestEvidenceStoredUsage(0, 0))
        whenever(versionRepository.storedUsageForUploader(any(), any())).thenReturn(InformationRequestEvidenceStoredUsage(0, 0))
        whenever(artifactRepository.findForRequirement(any())).thenReturn(emptyList())
        whenever(policyLoader.forBinding(any())).thenReturn(policy())
    }

    @Test
    fun `uploads are refused while evidence upload is unavailable in the deployment`()
    {
        whenever(deploymentPolicy.requireUploadAvailable()).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_UNAVAILABLE, "Unavailable"),
        )

        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_UNAVAILABLE) { admit(pdf(pages = 1)) }
    }

    @Test
    fun `a readable file is admitted with its inspection`()
    {
        val inspection = admit(pdf(pages = 2))

        assertEquals("application/pdf", inspection?.detectedMediaType)
        assertEquals(2, inspection?.pageCount)
    }

    @Test
    fun `a file larger than the platform allows is refused before anything is stored, more strictly without sign-in`()
    {
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(1_001)) }
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) {
            admit(sized(501), surface = InformationRequestEvidenceSurface.NO_AUTH)
        }
        admit(sized(501))
    }

    @Test
    fun `a request and each respondent on it hold only a bounded number and volume of files`()
    {
        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(3, 10))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(1, 2_950))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(0, 0))
        whenever(versionRepository.storedUsageForUploader(eq(fixture.request.id), eq(fixture.respondent)))
            .thenReturn(InformationRequestEvidenceStoredUsage(2, 10))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForUploader(eq(fixture.request.id), eq(fixture.respondent)))
            .thenReturn(InformationRequestEvidenceStoredUsage(1, 1_950))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }
    }

    @Test
    fun `a file outside the Requirement's size, count, total, or content type policy is refused`()
    {
        whenever(policyLoader.forBinding(any())).thenReturn(policy(maximumFileSize = 50))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) { admit(sized(51)) }

        whenever(policyLoader.forBinding(any())).thenReturn(policy(maximumFiles = 1))
        storedCurrent(content("existing"))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) { admit(sized(10)) }

        whenever(policyLoader.forBinding(any())).thenReturn(policy(maximumTotalSize = 20))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) { admit(sized(15)) }

        whenever(artifactRepository.findForRequirement(any())).thenReturn(emptyList())
        whenever(policyLoader.forBinding(any())).thenReturn(policy(acceptedTypes = setOf("application/pdf")))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) {
            admit(file("record.pdf", "plain text presented as a document"))
        }
        admit(pdf(pages = 1))
    }

    @Test
    fun `a file whose bytes are already provided for the Requirement is refused as a duplicate`()
    {
        val existing = pdf(pages = 1)
        storedCurrent(content(existing.file.readText(Charsets.ISO_8859_1)))

        assertRefused(InformationRequestErrorCatalog.EVIDENCE_DUPLICATE_CONTENT) { admit(existing) }
    }

    @Test
    fun `replacing a file with the same bytes to correct its attributes is not a duplicate`()
    {
        val existing = pdf(pages = 1)
        val artifact = storedCurrent(content(existing.file.readText(Charsets.ISO_8859_1)))

        admit(existing, replacing = artifact)
    }

    @Test
    fun `opaque ciphertext is stored for the record but never inspected or checked against content rules`()
    {
        whenever(policyLoader.forBinding(any())).thenReturn(policy(acceptedTypes = setOf("application/pdf")))

        val inspection = admit(file("sealed.bin", "ciphertext", DocumentEncryptionMode.END_TO_END))

        assertNull(inspection)
    }

    @Test
    fun `an inspection is recorded against the exact stored bytes of the new version`()
    {
        val stored = pdf(pages = 2)
        val inspection = admit(stored)
        val content = content(stored.file.readText(Charsets.ISO_8859_1))
        val version = InformationRequestEvidenceVersion().apply {
            evidenceArtifactId = UUID.randomUUID()
            informationRequestId = fixture.request.id
            createdByPrincipalKind = PrincipalKind.PARTICIPANT
            createdByPrincipalId = fixture.respondent.id
        }

        intake().recordInspection(version, content, inspection)

        val recorded = argumentCaptor<InformationRequestEvidenceAssessment>().also { verify(assessmentRepository).save(it.capture()) }.firstValue
        assertEquals(InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION, recorded.assessmentKind)
        assertEquals("INSPECTED", recorded.outcome)
        assertEquals(content.contentHash, recorded.contentHash)
        assertEquals(content.contentLength, recorded.contentLength)
        assertEquals(2, recorded.pageCount)
        assertEquals("application/pdf", recorded.detectedMediaType)
        assertTrue(!recorded.productionEligible)
    }

    @Test
    fun `nothing is recorded for opaque content that was never inspected`()
    {
        intake().recordInspection(InformationRequestEvidenceVersion(), content("ciphertext"), null)

        verify(assessmentRepository, never()).save(any())
    }

    private fun intake() = InformationRequestEvidenceIntake(
        deploymentPolicy = deploymentPolicy,
        limits = limits,
        policyLoader = policyLoader,
        inspector = InformationRequestEvidenceContentInspector(),
        artifactRepository = artifactRepository,
        versionRepository = versionRepository,
        assessmentRepository = assessmentRepository,
        documentVersionRecordingService = recordingService,
        clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC),
    )

    private fun admit(
        file: InformationRequestEvidenceFile,
        surface: InformationRequestEvidenceSurface = InformationRequestEvidenceSurface.AUTHENTICATED,
        replacing: InformationRequestEvidenceArtifact? = null,
    ) = intake().admit(
        fixture.request,
        fixture.documentRequirement,
        fixture.access,
        surface,
        file,
        DocumentVersionContentDigests.of(file.file),
        replacing,
    )

    private fun assertRefused(reasonCode: String, block: () -> Unit)
    {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { block() }
        assertEquals(reasonCode, refusal.reasonCode)
    }

    private fun storedCurrent(content: DocumentVersion): InformationRequestEvidenceArtifact
    {
        val artifact = InformationRequestEvidenceArtifact().apply {
            informationRequestId = fixture.request.id
            informationRequestRequirementId = fixture.documentRequirement.id
            artifactKey = "evidence-1"
            collectionState = InformationRequestEvidenceCollectionState.ACTIVE
        }
        val version = InformationRequestEvidenceVersion().apply {
            evidenceArtifactId = artifact.id
            informationRequestId = fixture.request.id
            InformationRequestEvidenceVersionSourceMapper.write(this, InformationRequestDocumentVersionEvidenceSource(content.id))
        }
        whenever(artifactRepository.findForRequirement(fixture.documentRequirement.id)).thenReturn(listOf(artifact))
        whenever(versionRepository.findLatest(artifact.id)).thenReturn(version)
        whenever(recordingService.findVersion(content.id)).thenReturn(content)
        return artifact
    }

    private fun content(bytes: String): DocumentVersion
    {
        val digest: DocumentVersionContentDigest = DocumentVersionContentDigests.of(
            directory.resolve("${UUID.randomUUID()}.bin").toFile().apply { writeText(bytes, Charsets.ISO_8859_1) },
        )
        return DocumentVersion().apply {
            document = Document()
            fileName = "record.pdf"
            storageProvider = DocumentVersionStorageProvider.OBJECT_STORE
            storageLocatorKind = DocumentVersionLocatorKind.OBJECT_KEY
            storageLocator = "document-versions/a/b/record.pdf"
            version = "1"
            createdDate = Timestamp.from(Instant.now())
            createdByPrincipalKind = PrincipalKind.PARTICIPANT
            createdByPrincipalId = fixture.respondent.id
            contentLength = digest.length
            contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
            contentHash = digest.value
            contentVerification = DocumentVersionContentVerification.VERIFIED
        }
    }

    private fun policy(
        maximumFiles: Int? = null,
        maximumFileSize: Long? = null,
        maximumTotalSize: Long? = null,
        acceptedTypes: Set<String> = emptySet(),
    ) = InformationRequestEvidencePolicy(
        minimumFileCount = 1,
        maximumFileCount = maximumFiles,
        maximumFileSizeBytes = maximumFileSize,
        maximumTotalSizeBytes = maximumTotalSize,
        minimumPageCount = null,
        maximumPageCount = null,
        attributeRequirements = emptyMap<InformationRequestEvidenceCapturedAttribute, InformationRequestEvidenceAttributeRequirement>(),
        acceptedValues = if (acceptedTypes.isEmpty()) emptyMap() else mapOf(InformationRequestEvidenceAttribute.CONTENT_TYPE to acceptedTypes),
        maximumIssueAgeDays = null,
        minimumRemainingValidityDays = null,
        minimumCoverageDays = null,
        coverageContinuityRequired = false,
        waiverPolicy = InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
        conformancePolicy = InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
    )

    private fun pdf(pages: Int): InformationRequestEvidenceFile
    {
        val target = directory.resolve("${UUID.randomUUID()}.pdf").toFile()
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "record.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun sized(bytes: Int): InformationRequestEvidenceFile =
        file("record.bin", "x".repeat(bytes))

    private fun file(
        name: String,
        content: String,
        encryptionMode: DocumentEncryptionMode = DocumentEncryptionMode.INTERNAL,
    ): InformationRequestEvidenceFile =
        InformationRequestEvidenceFile(
            directory.resolve("${UUID.randomUUID()}-$name").toFile().apply { writeText(content) },
            name,
            null,
            encryptionMode,
        )
}
