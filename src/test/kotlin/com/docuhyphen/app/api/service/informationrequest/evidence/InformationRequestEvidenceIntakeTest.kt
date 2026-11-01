package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.document.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

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
    private val executionGrantService: InformationRequestExecutionGrantService = mock()
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider = mock()
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
        whenever(versionRepository.storedUsageForUploaders(any(), any())).thenReturn(
            InformationRequestEvidenceStoredUsage(0, 0)
        )
        whenever(requirementContext.linkedIdentitiesOf(any())).thenAnswer { invocation ->
            setOf(invocation.arguments[0] as PrincipalRef)
        }
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
        whenever(versionRepository.storedUsageForUploaders(eq(fixture.request.id), eq(setOf(fixture.respondent))))
            .thenReturn(InformationRequestEvidenceStoredUsage(2, 10))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForUploaders(eq(fixture.request.id), eq(setOf(fixture.respondent))))
            .thenReturn(InformationRequestEvidenceStoredUsage(1, 1_950))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }
    }

    @Test
    fun `a respondent's allowance counts the files stored by every identity linked to it`() {
        val linkedAccount = PrincipalRef.user(UUID.randomUUID())
        val respondent = setOf(fixture.respondent, linkedAccount)
        whenever(requirementContext.linkedIdentitiesOf(fixture.respondent)).thenReturn(respondent)

        whenever(versionRepository.storedUsageForUploaders(eq(fixture.request.id), eq(respondent)))
            .thenReturn(InformationRequestEvidenceStoredUsage(2, 10))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForUploaders(eq(fixture.request.id), eq(respondent)))
            .thenReturn(InformationRequestEvidenceStoredUsage(1, 1_950))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForUploaders(eq(fixture.request.id), eq(respondent)))
            .thenReturn(InformationRequestEvidenceStoredUsage(1, 1_800))
        admit(sized(100))
    }

    @Test
    fun `an issued request holds only what its grant allows, beneath the platform ceiling`()
    {
        whenever(executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            RequestExecutionGrant().apply {
                evidenceFileAllowance = 1
                evidenceByteAllowance = 1_000
            },
        )
        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(1, 10))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(0, 950))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            RequestExecutionGrant().apply {
                evidenceFileAllowance = 10
                evidenceByteAllowance = 10_000
            },
        )
        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(3, 10))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(100)) }

        whenever(versionRepository.storedUsageForRequest(fixture.request.id)).thenReturn(InformationRequestEvidenceStoredUsage(2, 10))
        admit(sized(100))
    }

    @Test
    fun `a file outside the Requirement's size, count, total, or content type policy is refused in plain words`()
    {
        whenever(policyLoader.forBinding(any())).thenReturn(policy(maximumFileSize = 50))
        val tooLarge = assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) { admit(sized(51)) }

        whenever(policyLoader.forBinding(any())).thenReturn(policy(maximumFiles = 1))
        storedCurrent(content("existing"))
        val tooMany = assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) { admit(sized(10)) }

        whenever(policyLoader.forBinding(any())).thenReturn(policy(maximumTotalSize = 20))
        val tooMuch = assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) { admit(sized(15)) }

        whenever(artifactRepository.findForRequirement(any())).thenReturn(emptyList())
        whenever(policyLoader.forBinding(any())).thenReturn(policy(acceptedTypes = setOf("application/pdf")))
        val wrongType = assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) {
            admit(file("record.pdf", "plain text presented as a document"))
        }
        admit(pdf(pages = 1))

        assertEquals("This Requirement accepts files of at most 50 bytes", tooLarge.message)
        assertEquals("This Requirement accepts at most 1 file", tooMany.message)
        assertEquals("This Requirement accepts at most 20 bytes of files in total", tooMuch.message)
        assertTrue(
            wrongType.message.startsWith("This Requirement does not accept this type of file"),
            wrongType.message
        )
        listOf(tooLarge, tooMany, tooMuch, wrongType).forEach { refusal ->
            assertFalse(Regex("[A-Z]{2,}_[A-Z]").containsMatchIn(refusal.message), refusal.message)
        }
    }

    @Test
    fun `a limit refusal names the limit that was reached and states sizes in units a person reads`() {
        whenever(versionRepository.storedUsageForUploaders(any(), any()))
            .thenReturn(InformationRequestEvidenceStoredUsage(limits.maximumPartyFiles, 0))
        val tooMany = assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(10)) }

        whenever(versionRepository.storedUsageForUploaders(any(), any()))
            .thenReturn(InformationRequestEvidenceStoredUsage(0, limits.maximumPartyBytes))
        val tooMuch = assertRefused(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED) { admit(sized(10)) }

        assertEquals(
            "Each respondent stores at most ${limits.maximumPartyFiles} evidence files on a request",
            tooMany.message
        )
        assertTrue(
            Regex("Each respondent stores at most [0-9.]+ (KiB|MiB|GiB) of evidence on a request").matches(
                tooMuch.message
            ), tooMuch.message
        )
    }

    @Test
    fun `a file is accepted by the type its content and its declared type or name agree on`() {
        whenever(policyLoader.forBinding(any())).thenReturn(policy(acceptedTypes = setOf("text/csv")))

        admit(file("records.csv", "record,count\nfirst,1\n", declaredType = "text/csv"))
        admit(file("records.csv", "record,count\nfirst,1\n", declaredType = "application/vnd.ms-excel"))
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) {
            admit(file("notes.txt", "a note about the records", declaredType = "text/plain"))
        }
        assertRefused(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED) {
            admit(file("records.csv", "", declaredType = "text/csv").also {
                it.file.writeBytes(
                    byteArrayOf(
                        0,
                        1,
                        2,
                        3,
                        -1,
                        0,
                        0,
                        7
                    )
                )
            })
        }
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
    fun `content declared end-to-end encrypted is refused before storage because nothing could inspect or scan it`()
    {
        whenever(policyLoader.forBinding(any())).thenReturn(policy(acceptedTypes = setOf("application/pdf")))

        assertRefused(InformationRequestErrorCatalog.EVIDENCE_OPAQUE_CONTENT_REFUSED) {
            admit(file("page.html", "<html><body>content</body></html>", DocumentEncryptionMode.END_TO_END))
        }
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
    fun `opaque content is never inspected`()
    {
        assertNull(intake().inspect(file("sealed.bin", "ciphertext", DocumentEncryptionMode.END_TO_END)))
    }

    private fun intake() = InformationRequestEvidenceIntake(
        deploymentPolicy = deploymentPolicy,
        limits = limits,
        executionGrantService = executionGrantService,
        requirementContext = requirementContext,
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
    ) = intake().let { intake ->
        intake.admit(
            fixture.request,
            fixture.documentRequirement,
            fixture.access,
            surface,
            file,
            DocumentVersionContentDigests.of(file.file),
            intake.inspect(file),
            replacing,
        )
    }

    private fun assertRefused(reasonCode: String, block: () -> Unit): InformationRequestLifecycleException
    {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { block() }
        assertEquals(reasonCode, refusal.reasonCode)
        return refusal
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
        declaredType: String? = null,
    ): InformationRequestEvidenceFile =
        InformationRequestEvidenceFile(
            directory.resolve("${UUID.randomUUID()}-$name").toFile().apply { writeText(content) },
            name,
            declaredType,
            encryptionMode,
        )
}
