package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.model.InformationRequestEvidenceDtoMapper
import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceConformance
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFindingCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidencePolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.command.CommandPrecondition
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.UUID

class InformationRequestEvidenceQueryServiceTest
{
    private val fixture = InformationRequestEvidenceServiceFixture()
    private val queryService: InformationRequestQueryService = mock()
    private val auditRecorder: AuditRecorder = mock()

    private val uploads = fixture.uploadService()

    private val collection = fixture.collectionService()

    private val service = fixture.queryService(queryService, auditRecorder)

    init
    {
        whenever(queryService.findById(fixture.request.id, fixture.access)).thenReturn(fixture.request)
    }

    @Test
    fun `the assigned party lists the occurrence's evidence with every version and its evidence ETag`()
    {
        val first = uploaded("first-upload")
        val second = uploaded("second-upload")

        val listed = service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)

        assertEquals(listOf(first.id, second.id), listed.artifacts.map { it.artifact.id })
        assertEquals(1, listed.artifacts.first().versions.size)
        assertEquals(
            InformationRequestETag.evidenceOf(fixture.documentRequirement.id, fixture.artifacts),
            listed.evidenceETag,
        )
    }

    @Test
    fun `an evidence administrator lists the occurrence's evidence through the manage permission`()
    {
        uploaded("first-upload")
        deny(Action.INFORMATION_REQUEST_EVIDENCE_VIEW)

        val listed = service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)

        assertEquals(1, listed.artifacts.size)
    }

    @Test
    fun `a caller who may neither view nor manage the occurrence's evidence is refused`()
    {
        uploaded("first-upload")
        deny(Action.INFORMATION_REQUEST_EVIDENCE_VIEW)
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)

        assertThrows(ForbiddenException::class.java)
        {
            service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        }
        assertThrows(ForbiddenException::class.java)
        {
            service.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                fixture.artifacts.single().id,
                fixture.versions.single().id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
        verify(fixture.documentVersionRecordingService, never()).open(any())
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `the request read gate refuses before any evidence is read`()
    {
        whenever(queryService.findById(fixture.request.id, fixture.access)).thenThrow(ForbiddenException("No view"))

        assertThrows(ForbiddenException::class.java)
        {
            service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        }
        verify(fixture.artifactRepository, never()).findForRequirement(any())
    }

    @Test
    fun `removed evidence is listed only to an evidence administrator`()
    {
        val kept = uploaded("first-upload")
        val removed = uploaded("second-upload")
        collection.remove(
            ChangeInformationRequestEvidenceStateCommand(
                requestId = fixture.request.id,
                requirementId = fixture.documentRequirement.id,
                artifactId = removed.id,
                access = fixture.access,
                precondition = fixture.expectedArtifactETag(removed),
                idempotencyKey = "remove-key",
                reason = null,
            ),
        )
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)

        val respondentView = service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        assertEquals(listOf(kept.id), respondentView.artifacts.map { it.artifact.id })
        assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.artifact(fixture.request.id, fixture.documentRequirement.id, removed.id, fixture.access)
        }

        whenever(fixture.authorizationService.authorize(any(), eq(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE), any(), any()))
            .thenReturn(Decision.Allow())
        val administratorView = service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)
        assertEquals(listOf(kept.id, removed.id), administratorView.artifacts.map { it.artifact.id })
    }

    @Test
    fun `an authorized reader downloads a version's verified content and the access is audited`()
    {
        val artifact = uploaded("first-upload")
        val version = fixture.versions.single()
        val stored = pdfFile()
        whenever(fixture.documentVersionRecordingService.open(any())).thenReturn(DocumentVersionContent(stored, "collected-record.pdf"))

        val content = service.openContent(
            fixture.request.id,
            fixture.documentRequirement.id,
            artifact.id,
            version.id,
            fixture.access,
            InformationRequestEvidenceContentUse.DOWNLOAD,
        )

        assertEquals(stored, content.file)
        assertEquals("collected-record.pdf", content.fileName)
        assertEquals("application/pdf", content.mediaType)
        assertFalse(content.inline)
        val draft = recordedAudit()
        assertEquals(AuditEventType.INFORMATION_REQUEST_EVIDENCE_DOWNLOAD.key, draft.eventTypeKey)
        assertEquals(fixture.respondent.id, draft.actorId)
        assertEquals(fixture.request.id.toString(), draft.targetId)
        assertEquals(artifact.id.toString(), draft.payload["evidenceArtifactId"])
        assertEquals("1", draft.payload["evidenceVersionNumber"])
        assertEquals(fixture.documentRequirement.id.toString(), draft.payload["requirementId"])
        assertFalse(draft.payload.containsKey("declaredFileName"))
    }

    @Test
    fun `a preview is offered inline only for content a browser can show safely`()
    {
        val artifact = uploaded("first-upload")
        val version = fixture.versions.single()
        whenever(fixture.documentVersionRecordingService.open(any()))
            .thenReturn(DocumentVersionContent(pdfFile(), "collected-record.pdf"))

        val preview = service.openContent(
            fixture.request.id,
            fixture.documentRequirement.id,
            artifact.id,
            version.id,
            fixture.access,
            InformationRequestEvidenceContentUse.PREVIEW,
        )
        assertTrue(preview.inline)
        assertEquals(AuditEventType.INFORMATION_REQUEST_EVIDENCE_PREVIEW.key, recordedAudit().eventTypeKey)

        whenever(fixture.documentVersionRecordingService.open(any()))
            .thenReturn(DocumentVersionContent(markupFile(), "collected-record.pdf"))
        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                artifact.id,
                version.id,
                fixture.access,
                InformationRequestEvidenceContentUse.PREVIEW,
            )
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_PREVIEW_UNAVAILABLE, refusal.reasonCode)
    }

    @Test
    fun `a version is opened only through its own artifact and occurrence`()
    {
        val first = uploaded("first-upload")
        uploaded("second-upload")
        val secondVersion = fixture.versions.last()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                first.id,
                secondVersion.id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)

        val otherOccurrence = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.artifact(fixture.request.id, UUID.randomUUID(), first.id, fixture.access)
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, otherOccurrence.reasonCode)
        verify(fixture.documentVersionRecordingService, never()).open(any())
    }

    @Test
    fun `content that no longer matches its recorded digest is refused with a stable reason`()
    {
        val artifact = uploaded("first-upload")
        whenever(fixture.documentVersionRecordingService.open(any()))
            .thenThrow(DocumentVersionContentIntegrityException("document-versions/changed"))

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                artifact.id,
                fixture.versions.single().id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_UNAVAILABLE, refusal.reasonCode)
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `the list carries the Requirement's evidence evaluation and each version's conformance and findings`()
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
                conformancePolicy = InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
            ),
        )
        uploaded("first-upload")

        val listed = service.list(fixture.request.id, fixture.documentRequirement.id, fixture.access)

        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, listed.evaluation?.state)
        val dto = InformationRequestEvidenceDtoMapper.toDto(listed, fixture.respondent)
        assertEquals(InformationRequestEvidenceRequirementState.DEFICIENT, dto.evaluation?.state)
        assertFalse(dto.evaluation?.completesWork ?: true)
        val version = dto.artifacts.single().versions.single()
        assertEquals(InformationRequestEvidenceConformance.DEFICIENT, version.conformance)
        assertTrue(version.findings.any { it.code == InformationRequestEvidenceFindingCode.CONTENT_TYPE_MISMATCH && it.detail == "application/pdf" })
        assertTrue(version.findings.any { it.code == InformationRequestEvidenceFindingCode.NOT_SCANNED && !it.blocking })
    }

    @Test
    fun `quarantined content is refused before its bytes are read and the refusal is not recorded as access`()
    {
        val artifact = uploaded("first-upload")
        val version = fixture.versions.single()
        fixture.assessmentRepository.save(
            InformationRequestEvidenceAssessment().apply {
                informationRequestId = version.informationRequestId
                evidenceVersionId = version.id
                assessmentKind = InformationRequestEvidenceAssessmentKind.MALWARE_SCAN
                outcome = "MALWARE_DETECTED"
                contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
                contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                contentLength = 3
                engineName = "process-scanner"
                engineVersion = "1.0"
                signatureVersion = "2026.09.25"
                productionEligible = true
            },
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.openContent(
                fixture.request.id,
                fixture.documentRequirement.id,
                artifact.id,
                version.id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED, refusal.reasonCode)
        verify(fixture.documentVersionRecordingService, never()).open(any())
        verify(auditRecorder, never()).record(any())
    }

    private fun uploaded(idempotencyKey: String): InformationRequestEvidenceArtifact
    {
        val result = uploads.upload(
            UploadInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = fixture.documentRequirement.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = CommandPrecondition.Unconditioned,
                idempotencyKey = idempotencyKey,
                file = fixture.evidenceFile(content = "content $idempotencyKey"),
                attributes = InformationRequestEvidenceAttributes.NONE,
            ),
        )
        return result.artifact.artifact
    }

    private fun deny(action: Action)
    {
        whenever(fixture.authorizationService.authorize(any(), eq(action), any(), any()))
            .thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))
    }

    private fun recordedAudit(): AuditEventDraft
    {
        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, org.mockito.kotlin.atLeastOnce()).record(captor.capture())
        return captor.lastValue
    }

    private fun pdfFile(): File =
        File.createTempFile("evidence-content", ".bin").apply {
            deleteOnExit()
            writeBytes("%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".toByteArray())
        }

    private fun markupFile(): File =
        File.createTempFile("evidence-content", ".bin").apply {
            deleteOnExit()
            writeText("<html><body><script>alert(1)</script></body></html>")
        }
}
