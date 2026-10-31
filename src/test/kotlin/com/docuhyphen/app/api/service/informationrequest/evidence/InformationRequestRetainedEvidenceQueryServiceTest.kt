package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionReadHandle
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAccess
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.File
import java.nio.file.Files
import java.util.*

class InformationRequestRetainedEvidenceQueryServiceTest {
    private val fixture = InformationRequestEvidenceServiceFixture()
    private val queryService: InformationRequestQueryService = mock()
    private val auditRecorder: AuditRecorder = mock()
    private val noAuthAccess: InformationRequestNoAuthReadAccessService =
        InformationRequestNoAuthAccessDoubles.current()
    private val reviewAccess: InformationRequestReviewAccess = mock()
    private val uploads = fixture.uploadService()
    private val service = fixture.retainedQueryService(queryService, auditRecorder, noAuthAccess, reviewAccess)
    private val packageId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    init {
        whenever(queryService.findById(fixture.request.id, fixture.access)).thenReturn(fixture.request)
    }

    @Test
    fun `a package member opens after its occurrence is removed while the active collection refuses it`() {
        val group = fixture.groupRequirement
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(listOf(occurrence(group.occurrencePath)))
        val artifact = uploaded("first-upload", group)
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        val collection = fixture.queryService(queryService, auditRecorder, noAuthAccess)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            collection.openContent(
                fixture.request.id,
                group.id,
                artifact.id,
                version().id,
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
        val content = service.openContent(
            fixture.request.id,
            retained(artifact, group),
            fixture.access,
            InformationRequestEvidenceContentUse.DOWNLOAD
        )

        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, refusal.reasonCode)
        assertArrayEquals("content first-upload".toByteArray(), content.file.readBytes())
        assertEquals("collected-record.pdf", content.fileName)
        assertFalse(content.inline)
        content.close()
    }

    @Test
    fun `an opened package member is recorded with its package and item`() {
        val artifact = uploaded("first-upload")

        service.openContent(
            fixture.request.id,
            retained(artifact),
            fixture.access,
            InformationRequestEvidenceContentUse.DOWNLOAD
        ).close()

        val draft = recordedAudit()
        assertEquals(AuditEventType.INFORMATION_REQUEST_EVIDENCE_DOWNLOAD.key, draft.eventTypeKey)
        assertEquals(fixture.request.id.toString(), draft.targetId)
        assertEquals(
            mapOf(
                "requirementId" to fixture.documentRequirement.id.toString(),
                "evidenceArtifactId" to artifact.id.toString(),
                "evidenceVersionNumber" to "1",
                "submissionPackageId" to packageId.toString(),
                "submissionItemId" to itemId.toString(),
            ),
            draft.payload,
        )
    }

    @Test
    fun `a caller who may neither view nor manage the item's evidence nor read it in a review is refused before anything is read`() {
        val artifact = uploaded("first-upload")
        deny(Action.INFORMATION_REQUEST_EVIDENCE_VIEW)
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)

        assertThrows(ForbiddenException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }
        verify(reviewAccess).readsReviewedItem(
            fixture.access,
            fixture.request.id,
            packageId,
            fixture.documentRequirement.id
        )
        verify(fixture.documentVersionRecordingService, never()).open(any())
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `a caller who reads the item in a review of its submission opens the file the evidence permissions refuse`() {
        val artifact = uploaded("first-upload")
        deny(Action.INFORMATION_REQUEST_EVIDENCE_VIEW)
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)
        whenever(
            reviewAccess.readsReviewedItem(
                fixture.access,
                fixture.request.id,
                packageId,
                fixture.documentRequirement.id
            )
        )
            .thenReturn(true)

        val content = service.openContent(
            fixture.request.id,
            retained(artifact),
            fixture.access,
            InformationRequestEvidenceContentUse.DOWNLOAD
        )

        assertArrayEquals("content first-upload".toByteArray(), content.file.readBytes())
        assertEquals(packageId.toString(), recordedAudit().payload["submissionPackageId"])
        content.close()
    }

    @Test
    fun `a file a manager removed stays hidden from a caller who reads the item in a review`() {
        val artifact =
            uploaded("first-upload").apply { collectionState = InformationRequestEvidenceCollectionState.REMOVED }
        deny(Action.INFORMATION_REQUEST_EVIDENCE_VIEW)
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)
        whenever(reviewAccess.readsReviewedItem(any(), any(), any(), any())).thenReturn(true)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        verify(fixture.documentVersionRecordingService, never()).open(any())
    }

    @Test
    fun `a review reader whose access no longer reads the item once the file is read is neither served nor recorded`() {
        val artifact = uploaded("first-upload")
        val current =
            fixture.access.copy(authorization = fixture.access.authorization.copy(sessionRef = "current-session"))
        whenever(queryService.findById(fixture.request.id, current)).thenReturn(fixture.request)
        deny(Action.INFORMATION_REQUEST_EVIDENCE_VIEW)
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)
        whenever(
            reviewAccess.readsReviewedItem(
                fixture.access,
                fixture.request.id,
                packageId,
                fixture.documentRequirement.id
            )
        )
            .thenReturn(true)
        whenever(noAuthAccess.confirmCurrent(any(), any())).thenReturn(current)
        val directory = temporaryCopy(pdfBytes())

        assertThrows(ForbiddenException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }

        verify(reviewAccess).readsReviewedItem(current, fixture.request.id, packageId, fixture.documentRequirement.id)
        assertFalse(directory.exists())
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `the request read gate refuses before any package member is read`() {
        val artifact = uploaded("first-upload")
        whenever(queryService.findById(fixture.request.id, fixture.access)).thenThrow(ForbiddenException("Denied"))

        assertThrows(ForbiddenException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }
        verify(fixture.documentVersionRecordingService, never()).open(any())
    }

    @Test
    fun `a removed file stays hidden from a viewer but opens for an evidence administrator`() {
        val artifact =
            uploaded("first-upload").apply { collectionState = InformationRequestEvidenceCollectionState.REMOVED }
        deny(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE)

        val hidden = assertThrows(InformationRequestLifecycleException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, hidden.reasonCode)

        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE),
                any(),
                any()
            )
        )
            .thenReturn(Decision.Allow())
        service.openContent(
            fixture.request.id,
            retained(artifact),
            fixture.access,
            InformationRequestEvidenceContentUse.DOWNLOAD
        ).close()
    }

    @Test
    fun `a package member opens only as the exact version of the artifact and Requirement it names`() {
        val artifact = uploaded("first-upload")
        val other = uploaded("second-upload")
        val member = retained(artifact)

        listOf(
            member.copy(versionNumber = 2),
            member.copy(artifactId = other.id),
            member.copy(requirementId = fixture.fieldRequirement.id),
            member.copy(versionId = UUID.randomUUID()),
        ).forEach { named ->
            val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
                service.openContent(
                    fixture.request.id,
                    named,
                    fixture.access,
                    InformationRequestEvidenceContentUse.DOWNLOAD
                )
            }
            assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        }
        verify(fixture.documentVersionRecordingService, never()).open(any())
    }

    @Test
    fun `content whose stored identity is not the one the package froze is refused before it is read`() {
        val artifact = uploaded("first-upload")
        val member = retained(artifact)

        listOf(
            member.copy(contentHash = "0".repeat(64)),
            member.copy(contentLength = 1),
            member.copy(contentHashAlgorithm = null),
            member.copy(documentVersionId = UUID.randomUUID()),
        ).forEach { named ->
            val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
                service.openContent(
                    fixture.request.id,
                    named,
                    fixture.access,
                    InformationRequestEvidenceContentUse.DOWNLOAD
                )
            }
            assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_UNAVAILABLE, refusal.reasonCode)
        }
        verify(fixture.documentVersionRecordingService, never()).open(any())
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `quarantined content is refused before its bytes are read and is not recorded as access`() {
        val artifact = uploaded("first-upload")
        quarantine(version())

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }

        assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED, refusal.reasonCode)
        verify(fixture.documentVersionRecordingService, never()).open(any())
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `a file whose access ends while it is read is closed and neither recorded nor released`() {
        val artifact = uploaded("first-upload")
        val directory = temporaryCopy(pdfBytes())
        whenever(noAuthAccess.confirmCurrent(any(), any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED, "Revoked"),
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED, refusal.reasonCode)
        assertFalse(directory.exists())
        verify(auditRecorder, never()).record(any())
    }

    @Test
    fun `a package member previews inline only when a browser can show it safely`() {
        val artifact = uploaded("first-upload")
        temporaryCopy(pdfBytes())
        val preview = service.openContent(
            fixture.request.id,
            retained(artifact),
            fixture.access,
            InformationRequestEvidenceContentUse.PREVIEW
        )
        assertTrue(preview.inline)
        assertEquals(AuditEventType.INFORMATION_REQUEST_EVIDENCE_PREVIEW.key, recordedAudit().eventTypeKey)
        preview.close()

        val markup = temporaryCopy("<html><body><script>alert(1)</script></body></html>".toByteArray())
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.openContent(
                fixture.request.id,
                retained(artifact),
                fixture.access,
                InformationRequestEvidenceContentUse.PREVIEW
            )
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_PREVIEW_UNAVAILABLE, refusal.reasonCode)
        assertFalse(markup.exists())
    }

    private fun uploaded(
        idempotencyKey: String,
        requirement: InformationRequestRequirement = fixture.documentRequirement,
    ): InformationRequestEvidenceArtifact =
        uploads.upload(
            UploadInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = requirement.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = CommandPrecondition.Unconditioned,
                idempotencyKey = idempotencyKey,
                file = fixture.evidenceFile(content = "content $idempotencyKey"),
                attributes = InformationRequestEvidenceAttributes.NONE,
            ),
        ).artifact.artifact

    private fun version(artifact: InformationRequestEvidenceArtifact? = null): InformationRequestEvidenceVersion =
        fixture.versions.first { artifact == null || it.evidenceArtifactId == artifact.id }

    private fun retained(
        artifact: InformationRequestEvidenceArtifact,
        requirement: InformationRequestRequirement = fixture.documentRequirement,
    ): InformationRequestRetainedEvidenceVersion {
        val version = version(artifact)
        val document = fixture.documentVersions.getValue(requireNotNull(version.documentVersionId))
        return InformationRequestRetainedEvidenceVersion(
            packageId = packageId,
            submissionItemId = itemId,
            requirementId = requirement.id,
            artifactId = artifact.id,
            versionId = version.id,
            versionNumber = version.versionNumber,
            documentVersionId = document.id,
            contentHashAlgorithm = document.contentHashAlgorithm.name,
            contentHash = document.contentHash,
            contentLength = document.contentLength,
        )
    }

    private fun occurrence(path: String) = InformationRequestGroupOccurrence().apply {
        informationRequestId = fixture.request.id
        sourceTemplateGroupId = UUID.randomUUID()
        occurrencePath = path
    }

    private fun quarantine(version: InformationRequestEvidenceVersion) {
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
    }

    private fun deny(action: Action) {
        whenever(fixture.authorizationService.authorize(any(), eq(action), any(), any()))
            .thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))
    }

    private fun temporaryCopy(bytes: ByteArray): File {
        val directory = Files.createTempDirectory("retained-evidence-copy")
        val copy = directory.resolve("collected-record.pdf").toFile().apply { writeBytes(bytes) }
        whenever(fixture.documentVersionRecordingService.open(any())).thenAnswer {
            DocumentVersionContent(DocumentVersionReadHandle.temporaryCopy(copy, directory), "collected-record.pdf")
        }
        return directory.toFile()
    }

    private fun recordedAudit(): AuditEventDraft {
        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, atLeastOnce()).record(captor.capture())
        return captor.lastValue
    }

    private fun pdfBytes(): ByteArray = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".toByteArray()
}
