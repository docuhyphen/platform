package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributesMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestEvidenceReplacementAndWithdrawalTest
{
    private val fixture = InformationRequestEvidenceServiceFixture()

    private val uploads = fixture.uploadService()

    private val collection = fixture.collectionService()

    @Test
    fun `a replacement appends the next version and keeps every earlier version and its bytes`()
    {
        val artifact = uploaded()
        val firstDocumentVersion = fixture.versions.single().documentVersionId
        val revised = fixture.evidenceFile(content = "revised content", fileName = "collected-record-revised.pdf")
        val attributes = InformationRequestEvidenceAttributes(issuer = "Process Registry")

        val result = uploads.replace(replaceCommand(artifact, revised, attributes))

        assertEquals(listOf(1, 2), fixture.versions.map { it.versionNumber })
        assertEquals(firstDocumentVersion, fixture.versions.first().documentVersionId)
        val second = fixture.versions.last()
        assertEquals("collected-record-revised.pdf", second.declaredFileName)
        assertEquals(attributes, InformationRequestEvidenceAttributesMapper.read(second))
        assertEquals(fixture.respondent.id, second.createdByPrincipalId)
        assertEquals(2L, artifact.artifactRevision)
        assertEquals(listOf(1, 2), result.artifact.versions.map { it.version.versionNumber })
        assertEquals(InformationRequestETag.artifactOf(artifact), result.artifactETag)

        val firstDocument = fixture.documentVersions.getValue(requireNotNull(firstDocumentVersion)).document
        val secondDocument = fixture.documentVersions.getValue(requireNotNull(second.documentVersionId)).document
        assertEquals(firstDocument.id, secondDocument.id)
        assertEquals(DocumentVersionContentDigests.of(revised.file), fixture.recordedUploads.last().expectedDigest)
    }

    @Test
    fun `a replacement records transition history naming the version it added`()
    {
        val artifact = uploaded()

        uploads.replace(replaceCommand(artifact))

        val replacement = fixture.recordedTransitions().last()
        assertEquals(InformationRequestMutation.ADMINISTER_EVIDENCE, replacement.mutation)
        assertEquals(InformationRequestEvidenceAction.REPLACE, replacement.evidence?.action)
        assertEquals(2, replacement.evidence?.versionNumber)
        assertEquals(artifact.id, replacement.evidence?.artifactId)
    }

    @Test
    fun `a replacement states which artifact revision it replaces`()
    {
        val artifact = uploaded()

        val required = assertThrows(CommandPreconditionException::class.java)
        {
            uploads.replace(replaceCommand(artifact, precondition = CommandPrecondition.Absent))
        }
        assertEquals(CommandPreconditionException.Kind.REQUIRED, required.kind)

        val stale = assertThrows(CommandPreconditionException::class.java)
        {
            uploads.replace(
                replaceCommand(artifact, precondition = CommandPrecondition.ExpectedRevision("\"${artifact.id}:5\"")),
            )
        }
        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        assertEquals(1, fixture.versions.size)
    }

    @Test
    fun `an artifact under another occurrence is not replaced through this occurrence`()
    {
        val artifact = uploaded()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            uploads.replace(replaceCommand(artifact, requirementId = fixture.fieldRequirement.id))
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_NOT_COLLECTED, refusal.reasonCode)

        val missing = assertThrows(InformationRequestLifecycleException::class.java)
        {
            uploads.replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = fixture.request.id,
                    requirementId = fixture.documentRequirement.id,
                    artifactId = UUID.randomUUID(),
                    access = fixture.access,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = "replace-missing",
                    file = fixture.evidenceFile(),
                    attributes = InformationRequestEvidenceAttributes.NONE,
                ),
            )
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, missing.reasonCode)
        assertEquals(1, fixture.versions.size)
    }

    @Test
    fun `a retried replacement replays the recorded version without recording another`()
    {
        val artifact = uploaded()
        val revised = fixture.evidenceFile(content = "revised content")

        val first = uploads.replace(replaceCommand(artifact, revised))
        val replayed = uploads.replace(replaceCommand(artifact, revised, precondition = CommandPrecondition.Unconditioned))

        assertEquals(first.artifact.versions.map { it.version.id }, replayed.artifact.versions.map { it.version.id })
        assertEquals(first.artifactETag, replayed.artifactETag)
        assertEquals(2, fixture.versions.size)
    }

    @Test
    fun `a withdrawal marks the artifact withdrawn with who, when, and why and keeps every version`()
    {
        val artifact = uploaded()

        val result = collection.withdraw(stateCommand(artifact, reason = "Recorded in error"))

        assertEquals(InformationRequestEvidenceCollectionState.WITHDRAWN, artifact.collectionState)
        assertEquals(PrincipalKind.PARTICIPANT, artifact.stateChangedByPrincipalKind)
        assertEquals(fixture.respondent.id, artifact.stateChangedByPrincipalId)
        assertNotNull(artifact.stateChangedAt)
        assertEquals("Recorded in error", artifact.stateReason)
        assertEquals(2L, artifact.artifactRevision)
        assertEquals(1, fixture.versions.size)
        assertEquals(1, result.artifact.versions.size)
        assertEquals(InformationRequestETag.artifactOf(artifact), result.artifactETag)

        val withdrawal = fixture.recordedTransitions().last()
        assertEquals(InformationRequestEvidenceAction.WITHDRAW, withdrawal.evidence?.action)
        assertNull(withdrawal.evidence?.versionNumber)
    }

    @Test
    fun `a withdrawal requires the artifact revision and permission to withdraw evidence`()
    {
        val artifact = uploaded()

        assertThrows(CommandPreconditionException::class.java)
        {
            collection.withdraw(stateCommand(artifact, precondition = CommandPrecondition.Absent))
        }
        whenever(
            fixture.authorizationService.authorize(any(), eq(Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW), any(), any()),
        ).thenReturn(Decision.Deny("PARTY_NOT_ASSIGNED", "Not assigned"))
        assertThrows(ForbiddenException::class.java) { collection.withdraw(stateCommand(artifact)) }

        assertEquals(InformationRequestEvidenceCollectionState.ACTIVE, artifact.collectionState)
    }

    @Test
    fun `withdrawn evidence is neither withdrawn again nor replaced`()
    {
        val artifact = uploaded()
        collection.withdraw(stateCommand(artifact))

        val again = assertThrows(InformationRequestLifecycleException::class.java)
        {
            collection.withdraw(stateCommand(artifact, idempotencyKey = "withdraw-again"))
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_ARTIFACT_INACTIVE, again.reasonCode)

        val replacement = assertThrows(InformationRequestLifecycleException::class.java)
        {
            uploads.replace(replaceCommand(artifact))
        }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_ARTIFACT_INACTIVE, replacement.reasonCode)
        assertEquals(1, fixture.versions.size)
    }

    @Test
    fun `a retried withdrawal replays the recorded state without changing it again`()
    {
        val artifact = uploaded()
        val first = collection.withdraw(stateCommand(artifact))

        val replayed = collection.withdraw(stateCommand(artifact, precondition = CommandPrecondition.Unconditioned))

        assertEquals(first.artifactETag, replayed.artifactETag)
        assertEquals(2L, artifact.artifactRevision)
        assertEquals(2, fixture.recordedTransitions().size)
    }

    @Test
    fun `a replacement is admitted by the intake and the inspection of its new bytes is recorded`()
    {
        val artifact = uploaded()

        uploads.replace(replaceCommand(artifact))

        val replacement = fixture.versions.maxBy { it.versionNumber }
        val inspection = fixture.assessments.single { it.evidenceVersionId == replacement.id }
        assertEquals(fixture.documentVersions[replacement.documentVersionId]?.contentHash, inspection.contentHash)
        assertEquals(2, fixture.assessments.size)
    }

    private fun uploaded(): InformationRequestEvidenceArtifact
    {
        uploads.upload(
            UploadInformationRequestEvidenceCommand(
                requestId = fixture.request.id,
                requirementId = fixture.documentRequirement.id,
                access = fixture.access,
                surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                precondition = fixture.expectedEvidenceETag(),
                idempotencyKey = "upload-key",
                file = fixture.evidenceFile(),
                attributes = InformationRequestEvidenceAttributes.NONE,
            ),
        )
        return fixture.artifacts.single()
    }

    private fun replaceCommand(
        artifact: InformationRequestEvidenceArtifact,
        file: InformationRequestEvidenceFile = fixture.evidenceFile(content = "replacement content"),
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
        precondition: CommandPrecondition? = null,
        requirementId: UUID = fixture.documentRequirement.id,
    ) = ReplaceInformationRequestEvidenceCommand(
        requestId = fixture.request.id,
        requirementId = requirementId,
        artifactId = artifact.id,
        access = fixture.access,
        surface = InformationRequestEvidenceSurface.AUTHENTICATED,
        precondition = precondition ?: fixture.expectedArtifactETag(artifact),
        idempotencyKey = "replace-key",
        file = file,
        attributes = attributes,
    )

    private fun stateCommand(
        artifact: InformationRequestEvidenceArtifact,
        reason: String? = null,
        precondition: CommandPrecondition? = null,
        idempotencyKey: String = "withdraw-key",
    ) = ChangeInformationRequestEvidenceStateCommand(
        requestId = fixture.request.id,
        requirementId = fixture.documentRequirement.id,
        artifactId = artifact.id,
        access = fixture.access,
        precondition = precondition ?: fixture.expectedArtifactETag(artifact),
        idempotencyKey = idempotencyKey,
        reason = reason,
    )
}
