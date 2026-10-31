package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationStage
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseMutationGuard
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationContextLoader
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestGroupOccurrenceService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val groupOccurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val materializer: InformationRequestGroupOccurrenceMaterializer,
    private val structure: InformationRequestGroupOccurrenceStructure,
    private val mutationGuard: InformationRequestResponseMutationGuard,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val groupAuthorizationService: InformationRequestGroupAuthorizationService,
    private val lockService: InformationRequestSubmissionLockService,
    private val structuredResponseValidationService: InformationRequestStructuredResponseValidationService,
    private val validationContextLoader: InformationRequestStructuredResponseValidationContextLoader,
    private val noAuthAccess: InformationRequestNoAuthReadAccessService,
)
{
    @Transactional
    fun add(command: AddInformationRequestGroupOccurrenceCommand): InformationRequestGroupOccurrenceResult = runOnce(
        command.requestId,
        ADD_OPERATION,
        command.access,
        command.idempotencyKey,
        fingerprint(
            ADD_OPERATION,
            command.requestId,
            command.groupKey,
            command.parentOccurrenceId,
        ),
        replaySiblings = { request ->
            structure.activeSiblingsOf(
                request,
                command.groupKey,
                command.parentOccurrenceId
            )
        },
    ) { current -> addOccurrence(command.copy(access = current)) }

    @Transactional
    fun remove(command: RemoveInformationRequestGroupOccurrenceCommand): InformationRequestGroupOccurrenceResult =
        runOnce(
            command.requestId,
            REMOVE_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint(REMOVE_OPERATION, command.requestId, command.occurrenceId),
            replaySiblings = { request -> structure.remainingSiblingsOf(request, command.occurrenceId) },
        ) { current -> removeOccurrence(command.copy(access = current)) }

    @Transactional
    fun reorder(command: ReorderInformationRequestGroupOccurrencesCommand): InformationRequestGroupOccurrenceResult =
        runOnce(
            command.requestId,
            REORDER_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint(
                REORDER_OPERATION,
                command.requestId,
                command.groupKey,
                command.parentOccurrenceId,
                command.orderedOccurrenceIds,
            ),
            replaySiblings = { request ->
                structure.activeSiblingsOf(
                    request,
                    command.groupKey,
                    command.parentOccurrenceId
                )
            },
        ) { current -> reorderOccurrences(command.copy(access = current)) }

    private fun addOccurrence(
        command: AddInformationRequestGroupOccurrenceCommand,
    ): InformationRequestGroupOccurrenceResult =
        mutate(command.requestId, command.access, command.precondition) { request, now ->
            val template = materializer.templateOf(request)
            val group = structure.findGroup(template, command.groupKey)
                ?: throw ForbiddenException(InformationRequestGroupAuthorizationService.CHANGE_REFUSAL)
            groupAuthorizationService.authorizeGroupCreation(
                command.access,
                request,
                structure.materializedBindings(template, group),
                structure.bindingsUnder(template, group),
            )
            lockService.requireBindingsOpen(request, structure.bindingsUnder(template, group).map { it.id })
            val parent = structure.requireParent(request.id, group, command.parentOccurrenceId, template.groupsById)
            val allSiblings = groupOccurrenceRepository.findForGroupAndParentForUpdate(
                request.id,
                group.id,
                command.parentOccurrenceId,
            )
            val activeSiblings = allSiblings.filter { it.removedAt == null }
            structure.requireAddWithinMaximum(group, activeSiblings)
            val nextDisplayIndex = activeSiblings.maxOfOrNull { it.occurrenceIndex }?.plus(1) ?: 0
            val nextIdentityIndex = InformationRequestOccurrencePath.nextIdentityIndex(
                group.groupKey,
                parent?.occurrencePath,
                groupOccurrenceRepository.findAllForRequest(request.id).map { it.occurrencePath },
            )
            val occurrence =
                materializer.materialize(request, template, group, parent, nextDisplayIndex, nextIdentityIndex, now)
            val siblings = groupAuthorizationService.visibleOccurrences(
                command.access,
                request.id,
                (activeSiblings + occurrence).sortedBy { it.occurrenceIndex },
            )
            InformationRequestGroupOccurrenceResult(
                request,
                InformationRequestETag.responsesOf(request),
                siblings,
                changeOf("ADD", group.groupKey, occurrence),
            )
        }

    private fun removeOccurrence(
        command: RemoveInformationRequestGroupOccurrenceCommand,
    ): InformationRequestGroupOccurrenceResult =
        mutate(command.requestId, command.access, command.precondition) { request, now ->
            val template = materializer.templateOf(request)
            val target = groupOccurrenceRepository.findById(command.occurrenceId)
                ?.takeIf { it.informationRequestId == request.id }
                ?: throw ForbiddenException(InformationRequestGroupAuthorizationService.CHANGE_REFUSAL)
            val group = template.groupsById[target.sourceTemplateGroupId]
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.STATE_INVALID,
                    "Information Request group occurrence has no Template group",
                )
            groupAuthorizationService.authorizeGroupChange(
                command.access,
                request,
                structure.changeBindings(template, group)
            )
            val occurrence = groupOccurrenceRepository.findActiveByIdForUpdate(request.id, command.occurrenceId)
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request group occurrence not found",
                )
            lockService.requireBindingsOpen(request, structure.bindingsUnder(template, group).map { it.id })
            val activeSiblings = groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                request.id,
                group.id,
                occurrence.parentOccurrenceId,
            )
            if (activeSiblings.size <= group.minOccurrences)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_CARDINALITY_INVALID,
                    "This group already has the minimum number of occurrences",
                )
            }
            val removedScope = listOf(occurrence) + structure.activeDescendantsOf(request.id, listOf(occurrence))
            groupAuthorizationService.authorizeOccurrenceChange(
                command.access,
                request,
                removedScope.map { it.occurrencePath }.toSet(),
                structure.changeBindings(template, group),
            )
            val removedPaths = removedScope.map { it.occurrencePath }.toSet()
            val removedRequirementIds = requirementRepository.findForRequest(request.id)
                .filter { it.occurrencePath in removedPaths }
                .map { it.id }
            lockService.requireNotGivenOnce(request.id, removedRequirementIds)
            removedScope.forEach {
                it.removedAt = now
                it.removedByPrincipalKind = command.access.principal.kind
                it.removedByPrincipalId = command.access.principal.id
                groupOccurrenceRepository.update(it)
            }
            revisionRepository.closeOpen(removedRequirementIds, now)
            val remaining = groupAuthorizationService.visibleOccurrences(
                command.access,
                request.id,
                activeSiblings.filterNot { it.id == occurrence.id },
            )
            InformationRequestGroupOccurrenceResult(
                request,
                InformationRequestETag.responsesOf(request),
                remaining,
                changeOf("REMOVE", group.groupKey, occurrence),
            )
        }

    private fun reorderOccurrences(
        command: ReorderInformationRequestGroupOccurrencesCommand,
    ): InformationRequestGroupOccurrenceResult =
        mutate(command.requestId, command.access, command.precondition) { request, _ ->
            val template = materializer.templateOf(request)
            val group = structure.findGroup(template, command.groupKey)
                ?: throw ForbiddenException(InformationRequestGroupAuthorizationService.CHANGE_REFUSAL)
            groupAuthorizationService.authorizeGroupChange(
                command.access,
                request,
                structure.changeBindings(template, group)
            )
            lockService.requireBindingsOpen(request, structure.bindingsUnder(template, group).map { it.id })
            structure.requireParent(request.id, group, command.parentOccurrenceId, template.groupsById)
            val activeSiblings = groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
                request.id,
                group.id,
                command.parentOccurrenceId,
            )
            if (activeSiblings.map { it.id }.toSet() != command.orderedOccurrenceIds.toSet() ||
                activeSiblings.size != command.orderedOccurrenceIds.size
            )
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_ORDER_INVALID,
                    "The occurrence order must name every active sibling exactly once",
                )
            }
            groupAuthorizationService.authorizeOccurrenceChange(
                command.access,
                request,
                (activeSiblings + structure.activeDescendantsOf(request.id, activeSiblings)).map { it.occurrencePath }
                    .toSet(),
                structure.changeBindings(template, group),
            )
            val byId = activeSiblings.associateBy { it.id }
            command.orderedOccurrenceIds.forEachIndexed { index, occurrenceId ->
                val occurrence = requireNotNull(byId[occurrenceId])
                if (occurrence.occurrenceIndex != index)
                {
                    occurrence.occurrenceIndex = index
                    groupOccurrenceRepository.update(occurrence)
                }
            }
            val ordered = command.orderedOccurrenceIds.map { requireNotNull(byId[it]) }
            InformationRequestGroupOccurrenceResult(
                request,
                InformationRequestETag.responsesOf(request),
                ordered,
                mapOf(
                    "occurrenceChange" to "REORDER",
                    "groupKey" to group.groupKey,
                    "occurrenceIds" to ordered.joinToString(",") { it.id.toString() },
                ),
            )
        }

    private fun runOnce(
        requestId: UUID,
        operation: String,
        requested: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        replaySiblings: (InformationRequest) -> List<InformationRequestGroupOccurrence>,
        mutation: (RequestAccessContext) -> InformationRequestGroupOccurrenceResult,
    ): InformationRequestGroupOccurrenceResult
    {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, requested)
        mutationGuard.lock(requestId, "Information Request not found")
        val actor = noAuthAccess.requireCurrent(requestId, requested)
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(requestId),
            operation = operation,
            actor = CommandActorRef.principal(actor.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = mutation(actor)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayGroupOccurrenceResult(decision.result, actor, replaySiblings)
        }
    }

    private fun mutate(
        requestId: UUID,
        access: RequestAccessContext,
        precondition: CommandPrecondition,
        operation: (InformationRequest, Timestamp) -> InformationRequestGroupOccurrenceResult,
    ): InformationRequestGroupOccurrenceResult
    {
        val locked = mutationGuard.lock(requestId, "Information Request not found")
        val request = locked.request
        authorize(access, request.id)
        precondition.requireSatisfiedBy(InformationRequestETag.responsesOf(request))
        mutationGuard.requireResponseChangeAllowed(locked, OCCURRENCE_CHANGE_REFUSAL)
        val now = Timestamp.from(Instant.now())
        val result = operation(request, now)
        validateOccurrenceChange(request, access)
        request.responseRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.SAVE_RESPONSE,
                actor = InformationRequestAuditActor.of(access),
                reasonCode = GROUP_OCCURRENCE_CHANGE_REASON,
                auditIdempotencyKey = historyIdempotencyKey(request.id, result.responseETag),
                details = result.changeDetails,
            ),
        )
        return result.copy(responseETag = InformationRequestETag.responsesOf(request))
    }

    private fun changeOf(
        change: String,
        groupKey: String,
        occurrence: InformationRequestGroupOccurrence
    ): Map<String, String> =
        mapOf(
            "occurrenceChange" to change,
            "groupKey" to groupKey,
            "occurrenceId" to occurrence.id.toString(),
            "occurrencePath" to occurrence.occurrencePath,
        )

    private fun validateOccurrenceChange(request: InformationRequest, access: RequestAccessContext)
    {
        val stage = InformationRequestStructuredResponseValidationStage.OCCURRENCE_CHANGE
        if (!structuredResponseValidationService.appliesTo(stage)) return
        structuredResponseValidationService.validate(validationContextLoader.load(request, stage)) { requirementId ->
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                ResourceRef.informationRequestRequirement(requirementId),
                access.authorization,
            ) is Decision.Allow
        }
    }

    private fun authorize(access: RequestAccessContext, requestId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException(InformationRequestGroupAuthorizationService.CHANGE_REFUSAL)
        }
    }

    private fun replayGroupOccurrenceResult(
        result: CommandResultReference,
        access: RequestAccessContext,
        replaySiblings: (InformationRequest) -> List<InformationRequestGroupOccurrence>,
    ): InformationRequestGroupOccurrenceResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val locked = mutationGuard.lock(result.resourceId, "Information Request receipt target not found")
        val request = locked.request
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, access)
        authorize(access, request.id)
        mutationGuard.requireResponseChangeAllowed(locked, OCCURRENCE_CHANGE_REFUSAL)
        return InformationRequestGroupOccurrenceResult(
            request = request,
            responseETag = InformationRequestETag.responsesOf(request),
            occurrences = groupAuthorizationService.visibleOccurrences(
                access,
                request.id,
                replaySiblings(request).sortedBy { it.occurrenceIndex },
            ),
        )
    }

    private fun InformationRequestGroupOccurrenceResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            revision = request.responseRevision,
            etag = responseETag,
        )

    private fun fingerprint(operation: String, vararg fields: Any?): String =
        CommandRequestFingerprint.of(operation, *fields)

    private fun historyIdempotencyKey(requestId: UUID, responseETag: String): String =
        "information_request.group_occurrence|$requestId|$responseETag"

    private companion object
    {
        const val ADD_OPERATION = "add-information-request-group-occurrence"
        const val REMOVE_OPERATION = "remove-information-request-group-occurrence"
        const val REORDER_OPERATION = "reorder-information-request-group-occurrences"
        const val GROUP_OCCURRENCE_CHANGE_REASON = "GROUP_OCCURRENCES_CHANGED"
        const val OCCURRENCE_CHANGE_REFUSAL = "Information Request group occurrences cannot be changed now"
    }
}
