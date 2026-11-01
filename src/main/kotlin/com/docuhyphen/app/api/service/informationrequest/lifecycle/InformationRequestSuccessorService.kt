package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageSourcePackageRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestDraftFactory
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyMaterializationService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCarryForwardPlanner
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionPackageReader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestSuccessorService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val draftFactory: InformationRequestDraftFactory,
    private val partyRepository: InformationRequestPartyRepository,
    private val partyMaterialization: InformationRequestPartyMaterializationService,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val lockService: InformationRequestSubmissionLockService,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val lineageRepository: InformationRequestLineageRepository,
    private val carryForwardRepository: InformationRequestCarryForwardRepository,
    private val sourcePackageRepository: InformationRequestLineageSourcePackageRepository,
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
        InformationRequestReasonCodes.requireMachineCode(command.reasonCode)
        gate.requireVisibleTarget(command.access, command.sourceRequestId)
        val locked = gate.lock(command.sourceRequestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.sourceRequestId),
            operation = CREATE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                CREATE_OPERATION,
                command.sourceRequestId,
                command.kind,
                command.targetTemplateVersionId,
                command.sourcePackageId,
                command.reasonCode?.trim().orEmpty(),
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
        addressedRequestId: UUID = locked.request.id,
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
            is CommandReceiptDecision.Replayed -> replay(addressedRequestId, access, decision.result)
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
        gate.requireContinuation(locked, InformationRequestContinuationAction.SUPERSEDE)
        val preserved = preservedPackagesOf(source, spec.sourcePackageId)
        val sourcePackage = preserved.lastOrNull()
        val successor = draftFactory.createAlongside(
            locked.exchange,
            source,
            spec.targetTemplateVersionId?.let { followUpVersionOf(source, it) } ?: source.templateVersionId,
            access,
            idempotencyKey,
        )
        val notCopied = copyParties(source, successor, access)
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
        preserved.forEach { view ->
            sourcePackageRepository.save(
                InformationRequestLineageSourcePackage().apply {
                    lineageId = lineage.id
                    successorRequestId = successor.id
                    sourceRequestId = source.id
                    packageId = view.submissionPackage.id
                    createdAt = now
                },
            )
        }
        val carryForwards =
            if (preserved.isEmpty()) emptyList() else recordCarryForward(lineage, successor, preserved, now)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = source,
                fromState = source.state,
                toState = source.state,
                mutation = InformationRequestMutation.CREATE_SUCCESSOR,
                actor = InformationRequestAuditActor.of(access),
                reasonCode = spec.reasonCode,
                auditIdempotencyKey = "information_request.successor|${source.id}|${successor.id}",
                details = mapOf(
                    "successorRequestId" to successor.id.toString(),
                    "lineageKind" to spec.kind.name,
                ) + (sourcePackage?.let { mapOf("sourcePackageId" to it.submissionPackage.id.toString()) }
                    ?: emptyMap()) + (notCopied.takeIf { it.isNotEmpty() }
                    ?.let { mapOf("partiesNotCopied" to it.joinToString(",")) }
                    ?: emptyMap()),
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

    private fun preservedPackagesOf(
        source: InformationRequest,
        packageId: UUID?
    ): List<InformationRequestSubmissionPackageView>
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
            return listOf(view)
        }
        return lockService.activePackages(source.id)
            .sortedBy { it.packageNumber }
            .map { packageReader.view(source.id, it.id) }
    }

    private fun copyParties(
        source: InformationRequest,
        successor: InformationRequest,
        access: RequestAccessContext
    ): List<UUID>
    {
        val copies = partyMaterialization.copyEligibleParties(
            successor,
            source,
            partyRepository.findActiveForRequest(source.id)
                .filter { it.principalKind != null && it.principalId != null },
            access,
        )
        partyMaterialization.materializeSubjectParties(
            successor,
            partyRepository.findActiveForRequestRole(source.id, InformationRequestShareRoleKey.SUBJECT)
                .mapNotNull { it.subjectIdentityRefId },
            access,
        )
        return copies.notCopiedPartyIds
    }

    private fun followUpVersionOf(source: InformationRequest, targetTemplateVersionId: UUID): UUID {
        val current = templateVersionRepository.findById(source.templateVersionId)
        val target = templateVersionRepository.findById(targetTemplateVersionId)
        if (current == null || target == null ||
            target.templateDefinitionId != current.templateDefinitionId ||
            target.status != InformationRequestTemplateStatus.PUBLISHED ||
            target.versionNumber < current.versionNumber
        ) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUCCESSOR_TARGET_INVALID,
                "A follow-up uses its source's Template Version or a later published Version of the same Template",
            )
        }
        return target.id
    }

    private fun recordCarryForward(
        lineage: InformationRequestLineage,
        successor: InformationRequest,
        preserved: List<InformationRequestSubmissionPackageView>,
        now: Timestamp,
    ): List<InformationRequestCarryForward>
    {
        val packageOfItem = preserved.flatMap { view -> view.items.map { it.id to view.submissionPackage.id } }.toMap()
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
        return InformationRequestCarryForwardPlanner.plan(occurrences, preserved.flatMap { it.items }).map { planned ->
            carryForwardRepository.save(
                InformationRequestCarryForward().apply {
                    lineageId = lineage.id
                    informationRequestId = successor.id
                    informationRequestRequirementId = planned.requirementId
                    sourcePackageId = packageOfItem.getValue(planned.sourceItemId)
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
        addressedRequestId: UUID,
        access: RequestAccessContext,
        recorded: CommandResultReference,
    ): InformationRequestSuccessorResult
    {
        require(recorded.resourceType == ResourceType.INFORMATION_REQUEST) { "Command receipt does not reference a follow-up request" }
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW), addressedRequestId)
        val successor = requestRepository.findById(recorded.resourceId) ?: throw followUpNotFound()
        val lineage = lineageRepository.findForSuccessor(successor.id) ?: throw followUpNotFound()
        val source = requestRepository.findById(lineage.sourceRequestId) ?: throw followUpNotFound()
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW), source.id)
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW), successor.id)
        return InformationRequestSuccessorResult(
            source = source,
            successor = successor,
            lineage = lineage,
            carryForwards = carryForwardRepository.findForRequest(successor.id),
            successorETag = InformationRequestETag.aggregateOf(successor),
        )
    }

    private fun followUpNotFound() =
        InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Follow-up request not found")

    private companion object
    {
        const val CREATE_OPERATION = "create-information-request-successor"
    }
}
