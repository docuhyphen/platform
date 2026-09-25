package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceTransition
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
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
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestEvidenceCollectionService @Inject constructor(
    private val gate: InformationRequestEvidenceGate,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val viewLoader: InformationRequestEvidenceViewLoader,
)
{
    @Transactional
    fun withdraw(command: ChangeInformationRequestEvidenceStateCommand): InformationRequestEvidenceCommandResult =
        changeState(
            command,
            InformationRequestEvidenceAction.WITHDRAW,
            InformationRequestEvidenceCollectionState.WITHDRAWN,
            setOf(InformationRequestEvidenceCollectionState.ACTIVE),
            listOf(Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW),
        )

    @Transactional
    fun remove(command: ChangeInformationRequestEvidenceStateCommand): InformationRequestEvidenceCommandResult =
        changeState(
            command,
            InformationRequestEvidenceAction.REMOVE,
            InformationRequestEvidenceCollectionState.REMOVED,
            setOf(InformationRequestEvidenceCollectionState.ACTIVE, InformationRequestEvidenceCollectionState.WITHDRAWN),
            listOf(Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE),
        )

    private fun changeState(
        command: ChangeInformationRequestEvidenceStateCommand,
        action: InformationRequestEvidenceAction,
        target: InformationRequestEvidenceCollectionState,
        permittedFrom: Set<InformationRequestEvidenceCollectionState>,
        permittingActions: List<Action>,
    ): InformationRequestEvidenceCommandResult
    {
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT, command.artifactId),
            operation = operationOf(action),
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    operationOf(action),
                    command.requestId,
                    command.requirementId,
                    command.artifactId,
                    command.reason.orEmpty(),
                ).joinToString("|"),
            ),
        )

        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordStateChange(locked, command, action, target, permittedFrom, permittingActions)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT,
                        resourceId = result.artifact.artifact.id,
                        revision = result.artifact.artifact.artifactRevision,
                        etag = result.evidenceETag,
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, command, permittingActions, decision.result)
        }
    }

    private fun recordStateChange(
        locked: LockedInformationRequest,
        command: ChangeInformationRequestEvidenceStateCommand,
        action: InformationRequestEvidenceAction,
        target: InformationRequestEvidenceCollectionState,
        permittedFrom: Set<InformationRequestEvidenceCollectionState>,
        permittingActions: List<Action>,
    ): InformationRequestEvidenceCommandResult
    {
        val requirement = gate.requireEvidenceOccurrence(locked.request, command.requirementId)
        val artifact = ownedArtifact(command, forUpdate = true)
        gate.requireMutationAllowed(locked)
        gate.requireEvidenceOpen(locked, requirement)
        gate.requireContinuationEntitlement(locked)
        gate.authorizeAny(command.access, permittingActions, requirement.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.artifactOf(artifact))
        if (artifact.collectionState !in permittedFrom)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_ARTIFACT_INACTIVE,
                "This evidence is already ${artifact.collectionState.name.lowercase()}",
            )
        }

        val now = Timestamp.from(Instant.now())
        artifact.collectionState = target
        artifact.stateChangedAt = now
        artifact.stateChangedByPrincipalKind = command.access.principal.kind
        artifact.stateChangedByPrincipalId = command.access.principal.id
        artifact.stateReason = command.reason?.trim()?.ifBlank { null }
        artifact.artifactRevision += 1
        artifact.updatedAt = now
        artifactRepository.update(artifact)

        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = locked.request,
                fromState = locked.request.state,
                toState = locked.request.state,
                mutation = InformationRequestMutation.ADMINISTER_EVIDENCE,
                actor = command.access.principal,
                idempotencyKey = listOf(
                    AUDIT_KEY_PREFIX,
                    requirement.id,
                    "${command.access.principal.kind}:${command.access.principal.id}",
                    action,
                    command.idempotencyKey,
                ).joinToString("|"),
                evidence = InformationRequestEvidenceTransition(requirement.id, artifact.id, null, action),
            ),
        )

        return InformationRequestEvidenceCommandResult(
            artifact = viewLoader.view(artifact),
            evidenceETag = InformationRequestETag.evidenceOf(requirement.id, artifactRepository.findForRequirement(requirement.id)),
            artifactETag = InformationRequestETag.artifactOf(artifact),
        )
    }

    private fun replay(
        locked: LockedInformationRequest,
        command: ChangeInformationRequestEvidenceStateCommand,
        permittingActions: List<Action>,
        recorded: CommandResultReference,
    ): InformationRequestEvidenceCommandResult
    {
        val requirement = gate.requireEvidenceOccurrence(locked.request, command.requirementId)
        gate.requireMutationAllowed(locked)
        gate.requireContinuationEntitlement(locked)
        gate.authorizeAny(command.access, permittingActions, requirement.id)
        val artifact = ownedArtifact(command, forUpdate = false)
        val recordedRevision = requireNotNull(recorded.revision) { "Evidence receipt did not record a revision" }

        return InformationRequestEvidenceCommandResult(
            artifact = viewLoader.view(artifact),
            evidenceETag = requireNotNull(recorded.etag) { "Evidence receipt did not record an evidence ETag" },
            artifactETag = RevisionETag.of(artifact.id, recordedRevision),
        )
    }

    private fun ownedArtifact(
        command: ChangeInformationRequestEvidenceStateCommand,
        forUpdate: Boolean,
    ): InformationRequestEvidenceArtifact =
        (if (forUpdate) artifactRepository.findByIdForUpdate(command.artifactId) else artifactRepository.findById(command.artifactId))
            ?.takeIf { it.informationRequestRequirementId == command.requirementId }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request evidence not found",
            )

    private fun operationOf(action: InformationRequestEvidenceAction): String =
        "${action.name.lowercase()}-information-request-evidence"

    private companion object
    {
        const val AUDIT_KEY_PREFIX = "information_request.evidence"
    }
}
