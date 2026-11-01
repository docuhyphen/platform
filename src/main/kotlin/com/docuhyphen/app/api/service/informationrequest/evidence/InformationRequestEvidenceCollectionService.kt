package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAction
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceTransition
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
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
    private val noAuthAccess: InformationRequestNoAuthReadAccessService,
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

    fun withdrawForCondition(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        artifacts: List<InformationRequestEvidenceArtifact>,
        reason: String,
        access: RequestAccessContext,
        now: Timestamp,
    ): Boolean {
        artifacts.forEach { artifact ->
            gate.requireArtifactOpen(request, requirement, artifact.id)
            artifact.collectionState = InformationRequestEvidenceCollectionState.WITHDRAWN
            artifact.stateChangedAt = now
            artifact.stateChangedByPrincipalKind = access.principal.kind
            artifact.stateChangedByPrincipalId = access.principal.id
            artifact.stateReason = reason
            artifact.artifactRevision += 1
            artifact.updatedAt = now
            artifactRepository.update(artifact)
            transitionHistory.record(
                InformationRequestTransitionHistoryCommand(
                    request = request,
                    fromState = request.state,
                    toState = request.state,
                    mutation = InformationRequestMutation.ADMINISTER_EVIDENCE,
                    actor = InformationRequestAuditActor.of(access),
                    reasonCode = reason,
                    auditIdempotencyKey = listOf(
                        AUDIT_KEY_PREFIX,
                        requirement.id,
                        "${access.principal.kind}:${access.principal.id}",
                        InformationRequestEvidenceAction.WITHDRAW,
                        reason,
                        artifact.id,
                        artifact.artifactRevision,
                    ).joinToString("|"),
                    evidence = InformationRequestEvidenceTransition(
                        requirement.id,
                        artifact.id,
                        null,
                        InformationRequestEvidenceAction.WITHDRAW,
                    ),
                ),
            )
        }
        return artifacts.isNotEmpty()
    }

    @Transactional
    fun remove(command: ChangeInformationRequestEvidenceStateCommand): InformationRequestEvidenceCommandResult =
        changeState(
            command,
            InformationRequestEvidenceAction.REMOVE,
            InformationRequestEvidenceCollectionState.REMOVED,
            setOf(
                InformationRequestEvidenceCollectionState.ACTIVE,
                InformationRequestEvidenceCollectionState.WITHDRAWN
            ),
            listOf(Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE),
        )

    private fun changeState(
        requested: ChangeInformationRequestEvidenceStateCommand,
        action: InformationRequestEvidenceAction,
        target: InformationRequestEvidenceCollectionState,
        permittedFrom: Set<InformationRequestEvidenceCollectionState>,
        permittingActions: List<Action>,
    ): InformationRequestEvidenceCommandResult
    {
        gate.requireVisibleTarget(requested.access, requested.requestId)
        val locked = gate.lock(requested.requestId)
        val command = requested.copy(access = noAuthAccess.requireCurrent(requested.requestId, requested.access))
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT, command.artifactId),
            operation = operationOf(action),
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                operationOf(action),
                command.requestId,
                command.requirementId,
                command.artifactId,
                command.reason.orEmpty(),
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
            is CommandReceiptDecision.Replayed -> replay(locked, command, permittingActions)
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
        gate.requireRequestView(command.access, locked.request.id)
        gate.requireEvidenceTarget(command.access, command.requirementId, permittingActions)
        val requirement = gate.requireEvidenceOccurrence(locked.request, command.requirementId)
        val artifact = ownedArtifact(command, forUpdate = true)
        gate.requireMutationAllowed(locked)
        gate.requireArtifactOpen(locked, requirement, artifact.id)
        gate.requireContinuation(locked, InformationRequestContinuationAction.UPLOAD_WITHIN_ALLOWANCE)
        gate.authorizeAny(command.access, permittingActions, requirement.id)
        requireChangeable(locked, command, permittingActions, artifact)
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
                actor = InformationRequestAuditActor.of(command.access),
                auditIdempotencyKey = listOf(
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
            artifact = gate.forCaller(command.access, locked.request, viewLoader.view(artifact)),
            evidenceETag = InformationRequestETag.evidenceOf(
                requirement.id,
                artifactRepository.findForRequirement(requirement.id)
            ),
            artifactETag = InformationRequestETag.artifactOf(artifact),
        )
    }

    private fun requireChangeable(
        locked: LockedInformationRequest,
        command: ChangeInformationRequestEvidenceStateCommand,
        permittingActions: List<Action>,
        artifact: InformationRequestEvidenceArtifact,
    ) {
        val manages = Action.INFORMATION_REQUEST_EVIDENCE_MANAGE in permittingActions &&
                gate.permits(
                    command.access,
                    Action.INFORMATION_REQUEST_EVIDENCE_MANAGE,
                    artifact.informationRequestRequirementId
                )
        if (!manages) gate.requireChangeableBy(command.access, locked.request, artifact)
    }

    private fun replay(
        locked: LockedInformationRequest,
        command: ChangeInformationRequestEvidenceStateCommand,
        permittingActions: List<Action>,
    ): InformationRequestEvidenceCommandResult
    {
        gate.requireEvidenceTarget(command.access, command.requirementId, permittingActions)
        val requirement = gate.requireEvidenceOccurrence(locked.request, command.requirementId)
        gate.requireMutationAllowed(locked)
        gate.requireContinuation(locked, InformationRequestContinuationAction.UPLOAD_WITHIN_ALLOWANCE)
        gate.authorizeAny(command.access, permittingActions, requirement.id)
        val artifact = ownedArtifact(command, forUpdate = false)
        requireChangeable(locked, command, permittingActions, artifact)

        return InformationRequestEvidenceCommandResult(
            artifact = gate.forCaller(command.access, locked.request, viewLoader.view(artifact)),
            evidenceETag = InformationRequestETag.evidenceOf(
                requirement.id,
                artifactRepository.findForRequirement(requirement.id)
            ),
            artifactETag = InformationRequestETag.artifactOf(artifact),
        )
    }

    private fun ownedArtifact(
        command: ChangeInformationRequestEvidenceStateCommand,
        forUpdate: Boolean,
    ): InformationRequestEvidenceArtifact =
        (if (forUpdate) artifactRepository.findByIdForUpdate(command.artifactId)
        else artifactRepository.findById(
            command.artifactId
        ))
            ?.takeIf { it.informationRequestRequirementId == command.requirementId && gate.sees(command.access, it) }
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
