package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForward
import com.docuhyphen.app.api.model.entity.InformationRequestLineage
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestFollowUpSpec
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorOccurrence
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorResult
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestSuccessorService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val draftFactory: InformationRequestDraftFactory,
    private val partyRepository: InformationRequestPartyRepository,
    private val partyService: InformationRequestPartyService,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val lockService: InformationRequestSubmissionLockService,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val lineageRepository: InformationRequestLineageRepository,
    private val carryForwardRepository: InformationRequestCarryForwardRepository,
    private val requestRepository: InformationRequestRepository,
    private val lifecycleService: InformationRequestLifecycleService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun create(command: CreateInformationRequestSuccessorCommand): InformationRequestSuccessorResult
    {
        if (command.kind != InformationRequestLineageKind.SUPPLEMENT && command.kind != InformationRequestLineageKind.SUPERSEDING)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                "A recurring or refreshing request is created from its recurrence or refresh rule",
            )
        }
        val locked = gate.lock(command.sourceRequestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.sourceRequestId),
            operation = CREATE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    CREATE_OPERATION,
                    command.sourceRequestId,
                    command.kind,
                    command.targetTemplateVersionId?.toString().orEmpty(),
                    command.sourcePackageId?.toString().orEmpty(),
                    command.reasonCode?.trim().orEmpty(),
                ).joinToString("|"),
            ),
        )
        return run(locked, receipt, command.access) {
            val actions = if (command.kind == InformationRequestLineageKind.SUPPLEMENT)
                listOf(Action.INFORMATION_REQUEST_SUPERSEDE, Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT)
            else
                listOf(Action.INFORMATION_REQUEST_SUPERSEDE)
            gate.authorizeRequest(command.access, actions, locked.request.id)
            command.precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(locked.request))
            val result = follow(
                locked,
                InformationRequestFollowUpSpec(
                    kind = command.kind,
                    targetTemplateVersionId = command.targetTemplateVersionId,
                    sourcePackageId = command.sourcePackageId,
                    reasonCode = command.reasonCode,
                ),
                command.access,
                command.idempotencyKey,
            )
            if (command.kind == InformationRequestLineageKind.SUPERSEDING) supersede(result, command) else result
        }
    }

    fun run(
        locked: LockedInformationRequest,
        receipt: CommandReceiptRequest,
        access: RequestAccessContext,
        mutation: () -> InformationRequestSuccessorResult,
    ): InformationRequestSuccessorResult =
        when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = mutation()
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST,
                        resourceId = result.successor.id,
                        revision = result.successor.aggregateRevision,
                        etag = result.successorETag,
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, access, decision.result)
        }

    fun follow(
        locked: LockedInformationRequest,
        spec: InformationRequestFollowUpSpec,
        access: RequestAccessContext,
        idempotencyKey: String,
    ): InformationRequestSuccessorResult
    {
        val source = locked.request
        gate.requireMutation(locked, InformationRequestMutation.CREATE_SUCCESSOR)
        gate.requireContinuationEntitlement(locked)
        val sourcePackage = sourcePackageOf(source, spec.sourcePackageId)
        val successor = draftFactory.createAlongside(
            locked.exchange,
            source,
            spec.targetTemplateVersionId ?: source.templateVersionId,
            access,
            idempotencyKey,
        )
        copyActingParties(source, successor, access)
        val now = Timestamp.from(clock.instant())
        val lineage = lineageRepository.save(
            InformationRequestLineage().apply {
                successorRequestId = successor.id
                sourceRequestId = source.id
                sourcePackageId = sourcePackage?.submissionPackage?.id
                lineageKind = spec.kind
                recurrenceId = spec.recurrenceId
                recurrenceSequence = spec.recurrenceSequence
                refreshRuleId = spec.refreshRuleId
                reasonCode = spec.reasonCode?.trim()?.ifBlank { null }
                createdByPrincipalKind = access.principal.kind
                createdByPrincipalId = access.principal.id
                createdAt = now
            },
        )
        val carryForwards = sourcePackage?.let { recordCarryForward(lineage, successor, it, now) }.orEmpty()
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = source,
                fromState = source.state,
                toState = source.state,
                mutation = InformationRequestMutation.CREATE_SUCCESSOR,
                actor = access.principal,
                reasonCode = spec.reasonCode,
                idempotencyKey = "information_request.successor|${source.id}|${successor.id}",
                details = mapOf(
                    "successorRequestId" to successor.id.toString(),
                    "lineageKind" to spec.kind.name,
                ) + (sourcePackage?.let { mapOf("sourcePackageId" to it.submissionPackage.id.toString()) } ?: emptyMap()),
            ),
        )
        val current = requireNotNull(requestRepository.findById(successor.id))
        return InformationRequestSuccessorResult(
            source = source,
            successor = current,
            lineage = lineage,
            carryForwards = carryForwards,
            successorETag = InformationRequestETag.aggregateOf(current),
        )
    }

    private fun sourcePackageOf(source: InformationRequest, packageId: UUID?): InformationRequestSubmissionPackageView?
    {
        if (packageId != null)
        {
            val view = packageReader.view(source.id, packageId)
            if (view.withdrawal != null)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                    "A follow-up preserves a submission that was not withdrawn",
                )
            }
            return view
        }
        return lockService.activePackages(source.id).lastOrNull()?.let { packageReader.view(source.id, it.id) }
    }

    private fun copyActingParties(source: InformationRequest, successor: InformationRequest, access: RequestAccessContext)
    {
        val defaults = partyRepository.findActiveForRequest(source.id).mapNotNull { party ->
            val kind = party.principalKind ?: return@mapNotNull null
            val id = party.principalId ?: return@mapNotNull null
            BlueprintInformationRequestPartyDefault(party.roleKey, PrincipalRef(kind, id))
        }
        partyService.materializeBlueprintDefaultParties(successor, defaults, access)
    }

    private fun recordCarryForward(
        lineage: InformationRequestLineage,
        successor: InformationRequest,
        sourcePackage: InformationRequestSubmissionPackageView,
        now: Timestamp,
    ): List<InformationRequestCarryForward>
    {
        val occurrences = requirementRepository.findForRequest(successor.id).mapNotNull { requirement ->
            val templateRequirement = templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)
                ?: return@mapNotNull null
            InformationRequestSuccessorOccurrence(
                requirementId = requirement.id,
                requirementKey = templateRequirement.requirementKey,
                requirementType = templateRequirement.requirementType,
                occurrencePath = requirement.occurrencePath,
            )
        }
        return InformationRequestCarryForwardPlanner.plan(occurrences, sourcePackage.items).map { planned ->
            carryForwardRepository.save(
                InformationRequestCarryForward().apply {
                    lineageId = lineage.id
                    informationRequestId = successor.id
                    informationRequestRequirementId = planned.requirementId
                    sourcePackageId = sourcePackage.submissionPackage.id
                    sourceItemId = planned.sourceItemId
                    decision = planned.decision
                    reasonCode = planned.reasonCode
                    createdAt = now
                },
            )
        }
    }

    private fun supersede(
        result: InformationRequestSuccessorResult,
        command: CreateInformationRequestSuccessorCommand,
    ): InformationRequestSuccessorResult
    {
        val superseded = lifecycleService.supersede(
            SupersedeInformationRequestCommand(
                requestId = result.source.id,
                supersededByRequestId = result.successor.id,
                reasonCode = command.reasonCode,
                access = command.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(result.source)),
                idempotencyKey = "superseding-successor|${command.idempotencyKey}",
            ),
        )
        return result.copy(source = superseded.request)
    }

    private fun replay(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        recorded: CommandResultReference,
    ): InformationRequestSuccessorResult
    {
        require(recorded.resourceType == ResourceType.INFORMATION_REQUEST) { "Command receipt does not reference a follow-up request" }
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW), locked.request.id)
        val successor = requestRepository.findById(recorded.resourceId)
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Follow-up request not found")
        return InformationRequestSuccessorResult(
            source = locked.request,
            successor = successor,
            lineage = requireNotNull(lineageRepository.findForSuccessor(successor.id)),
            carryForwards = carryForwardRepository.findForRequest(successor.id),
            successorETag = requireNotNull(recorded.etag) { "Follow-up receipt did not record an ETag" },
        )
    }

    private companion object
    {
        const val CREATE_OPERATION = "create-information-request-successor"
    }
}
