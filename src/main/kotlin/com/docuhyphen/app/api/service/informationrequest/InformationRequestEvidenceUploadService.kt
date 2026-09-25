package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionUpload
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDocumentVersionEvidenceSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributesMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceTransition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionSourceMapper
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestEvidenceUploadService @Inject constructor(
    private val gate: InformationRequestEvidenceGate,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val viewLoader: InformationRequestEvidenceViewLoader,
    private val intake: InformationRequestEvidenceIntake,
)
{
    @Transactional
    fun upload(command: UploadInformationRequestEvidenceCommand): InformationRequestEvidenceCommandResult
    {
        val digest = DocumentVersionContentDigests.of(command.file.file)
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequestRequirement(command.requirementId),
            operation = UPLOAD_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = fingerprint(
                UPLOAD_OPERATION,
                command.requestId,
                command.requirementId,
                null,
                command.file,
                command.attributes,
                digest,
            ),
        )

        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordUpload(locked, command, digest)
                CommandMutationResult(result, reference(result))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, command.access, command.requirementId, decision.result)
        }
    }

    @Transactional
    fun replace(command: ReplaceInformationRequestEvidenceCommand): InformationRequestEvidenceCommandResult
    {
        val digest = DocumentVersionContentDigests.of(command.file.file)
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT, command.artifactId),
            operation = REPLACE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = fingerprint(
                REPLACE_OPERATION,
                command.requestId,
                command.requirementId,
                command.artifactId,
                command.file,
                command.attributes,
                digest,
            ),
        )

        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordReplacement(locked, command, digest)
                CommandMutationResult(result, reference(result))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, command.access, command.requirementId, decision.result)
        }
    }

    private fun recordUpload(
        locked: LockedInformationRequest,
        command: UploadInformationRequestEvidenceCommand,
        digest: DocumentVersionContentDigest,
    ): InformationRequestEvidenceCommandResult
    {
        val requirement = gate.requireEvidenceOccurrence(locked.request, command.requirementId)
        gate.requireMutationAllowed(locked)
        gate.requireEvidenceOpen(locked, requirement)
        gate.requireContinuationEntitlement(locked)
        gate.authorize(command.access, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD, requirement.id)
        val existing = artifactRepository.findForRequirement(requirement.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.evidenceOf(requirement.id, existing))
        val inspection = intake.admit(
            locked.request,
            requirement,
            command.access,
            command.surface,
            command.file,
            digest,
            replacing = null,
        )

        val documentVersion = documentVersionRecordingService.recordStandaloneDocument(
            documentVersionUpload(command.file, command.access, digest),
        )
        val now = Timestamp.from(Instant.now())
        val artifact = artifactRepository.save(
            InformationRequestEvidenceArtifact().apply {
                informationRequestId = locked.request.id
                informationRequestRequirementId = requirement.id
                artifactKey = "$ARTIFACT_KEY_PREFIX${existing.size + 1}"
                createdByPrincipalKind = command.access.principal.kind
                createdByPrincipalId = command.access.principal.id
                createdAt = now
                updatedAt = now
            },
        )
        val version = versionRepository.save(
            evidenceVersion(artifact, FIRST_VERSION, documentVersion, command.file, command.attributes, command.access, now),
        )
        intake.recordInspection(version, documentVersion, inspection)
        recordTransition(
            locked,
            command.access,
            command.idempotencyKey,
            InformationRequestEvidenceTransition(requirement.id, artifact.id, FIRST_VERSION, InformationRequestEvidenceAction.UPLOAD),
        )
        return result(requirement.id, artifact)
    }

    private fun recordReplacement(
        locked: LockedInformationRequest,
        command: ReplaceInformationRequestEvidenceCommand,
        digest: DocumentVersionContentDigest,
    ): InformationRequestEvidenceCommandResult
    {
        val requirement = gate.requireEvidenceOccurrence(locked.request, command.requirementId)
        val artifact = artifactRepository.findByIdForUpdate(command.artifactId)
            ?.takeIf { it.informationRequestRequirementId == requirement.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request evidence not found",
            )
        gate.requireMutationAllowed(locked)
        gate.requireEvidenceOpen(locked, requirement)
        gate.requireContinuationEntitlement(locked)
        gate.authorize(command.access, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD, requirement.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.artifactOf(artifact))
        if (artifact.collectionState != InformationRequestEvidenceCollectionState.ACTIVE)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_ARTIFACT_INACTIVE,
                "Withdrawn or removed evidence cannot be replaced",
            )
        }
        val inspection = intake.admit(
            locked.request,
            requirement,
            command.access,
            command.surface,
            command.file,
            digest,
            replacing = artifact,
        )

        val latest = versionRepository.findLatest(artifact.id)
            ?: error("An evidence artifact records at least one version")
        val upload = documentVersionUpload(command.file, command.access, digest)
        val document = latest.documentVersionId?.let(documentVersionRecordingService::findVersion)?.document
        val documentVersion = document
            ?.let { documentVersionRecordingService.recordNextVersion(it, upload) }
            ?: documentVersionRecordingService.recordStandaloneDocument(upload)
        val now = Timestamp.from(Instant.now())
        val versionNumber = latest.versionNumber + 1

        artifact.artifactRevision += 1
        artifact.updatedAt = now
        artifactRepository.update(artifact)
        val version = versionRepository.save(
            evidenceVersion(artifact, versionNumber, documentVersion, command.file, command.attributes, command.access, now),
        )
        intake.recordInspection(version, documentVersion, inspection)
        recordTransition(
            locked,
            command.access,
            command.idempotencyKey,
            InformationRequestEvidenceTransition(requirement.id, artifact.id, versionNumber, InformationRequestEvidenceAction.REPLACE),
        )
        return result(requirement.id, artifact)
    }

    private fun replay(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        requirementId: UUID,
        recorded: CommandResultReference,
    ): InformationRequestEvidenceCommandResult
    {
        require(recorded.resourceType == ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT)
        {
            "Command receipt does not reference Information Request evidence"
        }
        val requirement = gate.requireEvidenceOccurrence(locked.request, requirementId)
        gate.requireMutationAllowed(locked)
        gate.requireContinuationEntitlement(locked)
        gate.authorize(access, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD, requirement.id)
        val artifact = artifactRepository.findById(recorded.resourceId)
            ?.takeIf { it.informationRequestRequirementId == requirement.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request evidence receipt target not found",
            )
        val recordedRevision = requireNotNull(recorded.revision) { "Evidence receipt did not record a revision" }

        return InformationRequestEvidenceCommandResult(
            artifact = viewLoader.view(artifact, recordedRevision.toInt()),
            evidenceETag = requireNotNull(recorded.etag) { "Evidence receipt did not record an evidence ETag" },
            artifactETag = RevisionETag.of(artifact.id, recordedRevision),
        )
    }

    private fun result(requirementId: UUID, artifact: InformationRequestEvidenceArtifact): InformationRequestEvidenceCommandResult =
        InformationRequestEvidenceCommandResult(
            artifact = viewLoader.view(artifact),
            evidenceETag = InformationRequestETag.evidenceOf(requirementId, artifactRepository.findForRequirement(requirementId)),
            artifactETag = InformationRequestETag.artifactOf(artifact),
        )

    private fun reference(result: InformationRequestEvidenceCommandResult): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT,
            resourceId = result.artifact.artifact.id,
            revision = result.artifact.artifact.artifactRevision,
            etag = result.evidenceETag,
        )

    private fun documentVersionUpload(
        file: InformationRequestEvidenceFile,
        access: RequestAccessContext,
        digest: DocumentVersionContentDigest,
    ) = DocumentVersionUpload(
        fileName = file.declaredFileName,
        file = file.file,
        creator = access.principal,
        encryptionMode = file.encryptionMode,
        expectedDigest = digest,
    )

    private fun evidenceVersion(
        artifact: InformationRequestEvidenceArtifact,
        number: Int,
        documentVersion: DocumentVersion,
        file: InformationRequestEvidenceFile,
        attributes: InformationRequestEvidenceAttributes,
        access: RequestAccessContext,
        now: Timestamp,
    ) = InformationRequestEvidenceVersion().apply {
        evidenceArtifactId = artifact.id
        informationRequestId = artifact.informationRequestId
        versionNumber = number
        InformationRequestEvidenceVersionSourceMapper.write(
            this,
            InformationRequestDocumentVersionEvidenceSource(documentVersion.id),
        )
        createdByPrincipalKind = access.principal.kind
        createdByPrincipalId = access.principal.id
        createdBySessionRef = access.authorization.sessionRef?.takeIf { it.isNotBlank() }
        declaredFileName = file.declaredFileName
        declaredMediaType = file.declaredMediaType
        InformationRequestEvidenceAttributesMapper.write(this, attributes)
        createdAt = now
    }

    private fun recordTransition(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        idempotencyKey: String,
        evidence: InformationRequestEvidenceTransition,
    )
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = locked.request,
                fromState = locked.request.state,
                toState = locked.request.state,
                mutation = InformationRequestMutation.ADMINISTER_EVIDENCE,
                actor = access.principal,
                idempotencyKey = listOf(
                    AUDIT_KEY_PREFIX,
                    evidence.requirementId,
                    "${access.principal.kind}:${access.principal.id}",
                    evidence.action,
                    idempotencyKey,
                ).joinToString("|"),
                evidence = evidence,
            ),
        )
    }

    private fun fingerprint(
        operation: String,
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID?,
        file: InformationRequestEvidenceFile,
        attributes: InformationRequestEvidenceAttributes,
        digest: DocumentVersionContentDigest,
    ): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                operation,
                requestId,
                requirementId,
                artifactId ?: "",
                digest.algorithm,
                digest.value,
                digest.length,
                file.declaredFileName,
                file.declaredMediaType.orEmpty(),
                file.encryptionMode,
                attributes.issuer.orEmpty(),
                attributes.jurisdiction.orEmpty(),
                attributes.language.orEmpty(),
                attributes.issuedOn ?: "",
                attributes.expiresOn ?: "",
                attributes.coverage?.startsOn ?: "",
                attributes.coverage?.endsOn ?: "",
                attributes.certificationReference.orEmpty(),
                attributes.signatureReference.orEmpty(),
            ).joinToString("|"),
        )

    private companion object
    {
        const val UPLOAD_OPERATION = "upload-information-request-evidence"
        const val REPLACE_OPERATION = "replace-information-request-evidence"
        const val AUDIT_KEY_PREFIX = "information_request.evidence"
        const val ARTIFACT_KEY_PREFIX = "evidence-"
        const val FIRST_VERSION = 1
    }
}
