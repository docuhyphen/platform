package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFindingCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidencePolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanSettings
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.malwareOutcome
import com.docuhyphen.app.api.service.audit.AuditRecorder
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

class InformationRequestEvidenceFileBehaviorTest
{
    @TempDir
    lateinit var directory: Path

    private val fixture = InformationRequestEvidenceServiceFixture()
    private val requestQueries: InformationRequestQueryService = mock()
    private val uploads = fixture.uploadService()
    private val queries = fixture.queryService(requestQueries, mock<AuditRecorder>())
    private val scans = InformationRequestEvidenceMalwareAssessmentService(
        versionRepository = fixture.versionRepository,
        documentVersionRecordingService = fixture.documentVersionRecordingService,
        assessmentRepository = fixture.assessmentRepository,
        scanner = UnconfiguredInformationRequestEvidenceMalwareScanner,
        settings = InformationRequestEvidenceScanSettings(Duration.ofSeconds(5), Duration.ofDays(1), Duration.ofDays(30)),
        clock = Clock.systemUTC(),
        scanAudit = fixture.scanAudit,
    )

    init
    {
        whenever(requestQueries.findById(fixture.request.id, fixture.access)).thenReturn(fixture.request)
        withPolicy(InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE)
    }

    @Test
    fun `opaque ciphertext is kept for the record, never scanned clean, and never satisfies with a stable reason`()
    {
        upload(file("sealed.bin", "ciphertext bytes".toByteArray(), DocumentEncryptionMode.END_TO_END))
        val version = fixture.versions.single()

        assertEquals(InformationRequestEvidenceMalwareOutcome.SKIPPED, scans.assess(version.id).malwareOutcome())
        val listed = queries.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        val evaluation = listed.evaluation!!.versions.single()

        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, listed.evaluation?.state)
        assertTrue(evaluation.findings.any { it.code == InformationRequestEvidenceFindingCode.CONTENT_OPAQUE && it.code.blocking })
        assertEquals(1, fixture.recordedUploads.size)
    }

    @Test
    fun `a password-protected document is kept but never satisfies and is never offered for review`()
    {
        upload(protectedPdf())

        val listed = queries.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        val evaluation = listed.evaluation!!.versions.single()

        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, listed.evaluation?.state)
        assertTrue(evaluation.findings.any { it.code == InformationRequestEvidenceFindingCode.CONTENT_ENCRYPTED })
    }

    @Test
    fun `a document that cannot be parsed is kept and marked corrupt`()
    {
        upload(file("broken.pdf", "%PDF-1.7\nnot a document body".toByteArray()))

        val listed = queries.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)

        assertEquals(InformationRequestEvidenceConformance.CORRUPT, listed.evaluation!!.versions.single().conformance)
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, listed.evaluation?.state)
    }

    @Test
    fun `the same bytes are refused a second time for one occurrence and every stored byte is kept`()
    {
        val bytes = pdfBytes()
        upload(file("record.pdf", bytes), key = "first")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            upload(file("record-copy.pdf", bytes), key = "second")
        }

        assertEquals(InformationRequestErrorCatalog.EVIDENCE_DUPLICATE_CONTENT, refusal.reasonCode)
        assertEquals(1, fixture.recordedUploads.size)
    }

    private fun upload(file: InformationRequestEvidenceFile, key: String = "upload-key")
    {
        uploads.upload(
            UploadInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = fixture.documentRequirement.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = fixture.expectedEvidenceETag(),
                idempotencyKey = key,
                file = file,
                attributes = InformationRequestEvidenceAttributes.NONE,
            ),
        )
    }

    private fun withPolicy(conformance: InformationRequestEvidenceConformancePolicy)
    {
        whenever(fixture.policyLoader.forBinding(fixture.documentRequirement.sourceTemplateBindingId)).thenReturn(
            InformationRequestEvidencePolicy(
                minimumFileCount = 1,
                maximumFileCount = null,
                maximumFileSizeBytes = null,
                maximumTotalSizeBytes = null,
                minimumPageCount = null,
                maximumPageCount = null,
                attributeRequirements = emptyMap(),
                acceptedValues = emptyMap(),
                maximumIssueAgeDays = null,
                minimumRemainingValidityDays = null,
                minimumCoverageDays = null,
                coverageContinuityRequired = false,
                waiverPolicy = InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
                conformancePolicy = conformance,
            ),
        )
    }

    private fun file(
        name: String,
        bytes: ByteArray,
        encryptionMode: DocumentEncryptionMode = DocumentEncryptionMode.INTERNAL,
    ): InformationRequestEvidenceFile =
        InformationRequestEvidenceFile(
            directory.resolve("${System.nanoTime()}-$name").toFile().apply { writeBytes(bytes) },
            name,
            null,
            encryptionMode,
        )

    private fun pdfBytes(): ByteArray
    {
        val target = directory.resolve("${System.nanoTime()}.pdf").toFile()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(target)
        }
        return target.readBytes()
    }

    private fun protectedPdf(): InformationRequestEvidenceFile
    {
        val target: File = directory.resolve("${System.nanoTime()}-protected.pdf").toFile()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(StandardProtectionPolicy("owner-secret", "user-secret", AccessPermission()).apply {
                encryptionKeyLength = 128
            })
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "protected.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }
}
