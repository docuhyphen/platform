package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceSourceKind
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributesMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

class InformationRequestEvidenceUploadServiceTest
{
    private val fixture = InformationRequestEvidenceServiceFixture()

    private val service = fixture.uploadService()

    @Test
    fun `an upload records one artifact and its first version under the exact occurrence`()
    {
        val file = fixture.evidenceFile()
        val attributes = InformationRequestEvidenceAttributes(
            issuer = "Process Registry",
            issuedOn = LocalDate.of(2026, 1, 10),
            coverage = InformationRequestEvidenceCoverage(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
        )

        val result = service.upload(command(file = file, attributes = attributes))

        val artifact = fixture.artifacts.single()
        assertEquals(fixture.request.id, artifact.informationRequestId)
        assertEquals(fixture.documentRequirement.id, artifact.informationRequestRequirementId)
        assertEquals("evidence-1", artifact.artifactKey)
        assertEquals(1L, artifact.artifactRevision)
        assertEquals(PrincipalKind.PARTICIPANT, artifact.createdByPrincipalKind)
        assertEquals(fixture.respondent.id, artifact.createdByPrincipalId)
        assertEquals(InformationRequestEvidenceCollectionState.ACTIVE, artifact.collectionState)

        val version = fixture.versions.single()
        assertEquals(artifact.id, version.evidenceArtifactId)
        assertEquals(fixture.request.id, version.informationRequestId)
        assertEquals(1, version.versionNumber)
        assertEquals(InformationRequestEvidenceSourceKind.DOCUMENT_VERSION, version.sourceKind)
        assertEquals(PrincipalKind.PARTICIPANT, version.createdByPrincipalKind)
        assertEquals(fixture.respondent.id, version.createdByPrincipalId)
        assertEquals("verified-session", version.createdBySessionRef)
        assertEquals("collected-record.pdf", version.declaredFileName)
        assertEquals("application/pdf", version.declaredMediaType)
        assertEquals(attributes, InformationRequestEvidenceAttributesMapper.read(version))

        val stored = fixture.recordedUploads.single()
        assertEquals("collected-record.pdf", stored.fileName)
        assertEquals(file.file, stored.file)
        assertEquals(fixture.respondent, stored.creator)
        assertEquals(DocumentEncryptionMode.INTERNAL, stored.encryptionMode)
        assertEquals(DocumentVersionContentDigests.of(file.file), stored.expectedDigest)
        assertEquals(fixture.documentVersions.keys.single(), version.documentVersionId)

        assertEquals(artifact.id, result.artifact.artifact.id)
        assertEquals(listOf(version.id), result.artifact.versions.map { it.version.id })
        assertEquals(
            InformationRequestETag.evidenceOf(fixture.documentRequirement.id, fixture.artifacts),
            result.evidenceETag,
        )
        assertEquals(InformationRequestETag.artifactOf(artifact), result.artifactETag)
        verify(fixture.authorizationService).authorize(
            eq(fixture.respondent),
            eq(Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD),
            eq(ResourceRef.informationRequestRequirement(fixture.documentRequirement.id)),
            eq(fixture.access.authorization),
        )
    }

    @Test
    fun `an upload records transition history naming the evidence it added`()
    {
        service.upload(command())

        val transition = fixture.recordedTransitions().single()
        assertEquals(InformationRequestMutation.ADMINISTER_EVIDENCE, transition.mutation)
        assertEquals(fixture.respondent, transition.actor)
        assertEquals(InformationRequestState.ISSUED, transition.fromState)
        assertEquals(InformationRequestState.ISSUED, transition.toState)
        val evidence = requireNotNull(transition.evidence)
        assertEquals(fixture.documentRequirement.id, evidence.requirementId)
        assertEquals(fixture.artifacts.single().id, evidence.artifactId)
        assertEquals(1, evidence.versionNumber)
        assertEquals(InformationRequestEvidenceAction.UPLOAD, evidence.action)
        assertTrue(requireNotNull(transition.idempotencyKey).startsWith("information_request.evidence|"))
    }

    @Test
    fun `a second upload to the same occurrence adds an independently keyed artifact`()
    {
        service.upload(command(idempotencyKey = "first-upload"))

        val second = service.upload(command(idempotencyKey = "second-upload", file = fixture.evidenceFile(content = "another record")))

        assertEquals(listOf("evidence-1", "evidence-2"), fixture.artifacts.map { it.artifactKey })
        assertEquals("\"${fixture.documentRequirement.id}:2\"", second.evidenceETag)
    }

    @Test
    fun `an upload states which evidence it expects to change`()
    {
        val required = assertThrows(CommandPreconditionException::class.java)
        {
            service.upload(command(precondition = CommandPrecondition.Absent))
        }
        assertEquals(CommandPreconditionException.Kind.REQUIRED, required.kind)

        val stale = assertThrows(CommandPreconditionException::class.java)
        {
            service.upload(
                command(precondition = CommandPrecondition.ExpectedRevision("\"${fixture.documentRequirement.id}:7\"")),
            )
        }
        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        assertNothingRecorded()
    }

    @Test
    fun `an upload is refused when the caller may not upload evidence to the occurrence`()
    {
        whenever(fixture.authorizationService.authorize(any(), eq(Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD), any(), any()))
            .thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))

        assertThrows(ForbiddenException::class.java) { service.upload(command()) }
        assertNothingRecorded()
    }

    @Test
    fun `an upload is refused for a Requirement that does not collect evidence`()
    {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.upload(command(requirementId = fixture.fieldRequirement.id))
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_NOT_COLLECTED, refusal.reasonCode)
        assertNothingRecorded()
    }

    @Test
    fun `an upload is refused for an occurrence that belongs to another request`()
    {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.upload(command(requirementId = UUID.randomUUID()))
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        assertNothingRecorded()
    }

    @Test
    fun `an upload is refused for a removed group occurrence`()
    {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.upload(command(requirementId = fixture.groupRequirement.id))
        }
        assertEquals(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, refusal.reasonCode)
        assertNothingRecorded()
    }

    @Test
    fun `an upload is refused while the request or its Exchange does not accept responses`()
    {
        fixture.request.state = InformationRequestState.DRAFT
        val draft = assertThrows(InformationRequestLifecycleException::class.java) { service.upload(command()) }
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, draft.reasonCode)

        fixture.request.state = InformationRequestState.ISSUED
        fixture.exchange.status = ExchangeStatus.INITIATED
        val initiated = assertThrows(InformationRequestLifecycleException::class.java) { service.upload(command()) }
        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, initiated.reasonCode)
        assertNothingRecorded()
    }

    @Test
    fun `an upload is refused once the request's execution grant is revoked`()
    {
        fixture.grant.revokedAt = java.sql.Timestamp.from(java.time.Instant.now())

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { service.upload(command()) }
        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
        assertNothingRecorded()
    }

    @Test
    fun `a retried upload replays the recorded artifact without storing its content again`()
    {
        val file = fixture.evidenceFile()
        val first = service.upload(command(file = file))

        val replayed = service.upload(command(file = file, precondition = CommandPrecondition.Unconditioned))

        assertEquals(first.artifact.artifact.id, replayed.artifact.artifact.id)
        assertEquals(first.artifact.versions.map { it.version.id }, replayed.artifact.versions.map { it.version.id })
        assertEquals(first.evidenceETag, replayed.evidenceETag)
        assertEquals(1, fixture.artifacts.size)
        assertEquals(1, fixture.recordedUploads.size)
        verify(fixture.transitionHistory, times(1)).record(any())
    }

    @Test
    fun `a replayed upload is refused once the caller may no longer upload to the occurrence`()
    {
        val file = fixture.evidenceFile()
        service.upload(command(file = file))
        whenever(fixture.authorizationService.authorize(any(), eq(Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD), any(), any()))
            .thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))

        assertThrows(ForbiddenException::class.java)
        {
            service.upload(command(file = file, precondition = CommandPrecondition.Unconditioned))
        }
    }

    @Test
    fun `a retried upload with different content under the same key is refused as a conflict`()
    {
        service.upload(command(file = fixture.evidenceFile(content = "first content")))

        assertThrows(CommandReceiptConflictException::class.java)
        {
            service.upload(
                command(
                    file = fixture.evidenceFile(content = "other content"),
                    precondition = CommandPrecondition.Unconditioned,
                ),
            )
        }
        assertEquals(1, fixture.recordedUploads.size)
    }

    @Test
    fun `a second upload of bytes already provided for the occurrence is refused as a duplicate`()
    {
        service.upload(command(idempotencyKey = "first-upload"))

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.upload(command(idempotencyKey = "second-upload"))
        }

        assertEquals(InformationRequestErrorCatalog.EVIDENCE_DUPLICATE_CONTENT, refusal.reasonCode)
        assertEquals(1, fixture.artifacts.size)
        assertEquals(1, fixture.recordedUploads.size)
    }

    @Test
    fun `an upload the intake refuses stores nothing`()
    {
        whenever(fixture.deploymentPolicy.requireUploadAvailable()).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_UNAVAILABLE, "Evidence upload is not enabled"),
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { service.upload(command()) }

        assertEquals(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_UNAVAILABLE, refusal.reasonCode)
        assertNothingRecorded()
    }

    @Test
    fun `an upload without sign-in is held to the stricter file size limit`()
    {
        val large = fixture.evidenceFile(content = "x".repeat(600_000))

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            service.upload(command(file = large, surface = InformationRequestEvidenceSurface.NO_AUTH))
        }

        assertEquals(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED, refusal.reasonCode)
        assertNothingRecorded()
        service.upload(command(file = large, idempotencyKey = "signed-in-upload"))
        assertEquals(1, fixture.artifacts.size)
    }

    @Test
    fun `a new version's content inspection is recorded against the bytes that were stored`()
    {
        service.upload(command())

        val inspection = fixture.assessments.single()
        assertEquals(InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION, inspection.assessmentKind)
        assertEquals(fixture.versions.single().id, inspection.evidenceVersionId)
        assertEquals(fixture.documentVersions.values.single().contentHash, inspection.contentHash)
        assertEquals(fixture.documentVersions.values.single().contentLength, inspection.contentLength)
    }

    @Test
    fun `opaque content is stored for the record but never inspected`()
    {
        service.upload(command(file = fixture.evidenceFile(encryptionMode = DocumentEncryptionMode.END_TO_END)))

        assertEquals(1, fixture.versions.size)
        assertTrue(fixture.assessments.isEmpty())
    }

    private fun command(
        requirementId: UUID = fixture.documentRequirement.id,
        file: InformationRequestEvidenceFile = fixture.evidenceFile(),
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
        precondition: CommandPrecondition? = null,
        idempotencyKey: String = "upload-key",
        surface: InformationRequestEvidenceSurface = InformationRequestEvidenceSurface.AUTHENTICATED,
    ) = UploadInformationRequestEvidenceCommand(
        requestId = fixture.request.id,
        requirementId = requirementId,
        access = fixture.access,
        surface = surface,
        precondition = precondition ?: fixture.expectedEvidenceETag(),
        idempotencyKey = idempotencyKey,
        file = file,
        attributes = attributes,
    )

    private fun assertNothingRecorded()
    {
        assertTrue(fixture.artifacts.isEmpty())
        assertTrue(fixture.versions.isEmpty())
        verify(fixture.documentVersionRecordingService, never()).recordStandaloneDocument(any())
        verify(fixture.transitionHistory, never()).record(any())
    }
}
