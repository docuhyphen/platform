package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceCapturedAttribute
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidencePolicyLoader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant

@ApplicationScoped
class InformationRequestFollowUpService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val successorService: InformationRequestSuccessorService,
    private val recurrenceRepository: InformationRequestRecurrenceRepository,
    private val refreshRuleRepository: InformationRequestRefreshRuleRepository,
    private val lineageRepository: InformationRequestLineageRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val evidencePolicies: InformationRequestEvidencePolicyLoader,
    private val clock: Clock,
    private val requestRepository: InformationRequestRepository,
)
{
    @Transactional
    fun defineRecurrence(command: DefineInformationRequestRecurrenceCommand): InformationRequestRecurrenceDefinition
    {
        InformationRequestFollowUpScheduleBounds.requireValid(command)
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef.informationRequest(command.requestId),
            DEFINE_RECURRENCE_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(command.intervalUnit, command.intervalCount, command.firstDueAt, command.maximumOccurrences),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                schedule(locked, command.access, command.precondition)
                if (recurrenceRepository.findForOrigin(locked.request.id) != null)
                {
                    throw InformationRequestLifecycleException(
                        InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                        "This request already recurs",
                    )
                }
                val recurrence = recurrenceRepository.save(
                    InformationRequestRecurrence().apply {
                        originRequestId = locked.request.id
                        intervalUnit = command.intervalUnit
                        intervalCount = command.intervalCount
                        firstDueAt = Timestamp.from(command.firstDueAt)
                        maximumOccurrences = command.maximumOccurrences
                        createdByPrincipalKind = command.access.principal.kind
                        createdByPrincipalId = command.access.principal.id
                        createdAt = Timestamp.from(clock.instant())
                    },
                )
                val requestETag = advance(locked)
                recordSchedule(
                    locked,
                    command.access,
                    command.idempotencyKey,
                    mapOf("recurrenceId" to recurrence.id.toString())
                )
                CommandMutationResult(
                    InformationRequestRecurrenceDefinition(recurrence, requestETag),
                    resultOf(locked, requestETag)
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> {
                authorizeReplay(locked, command.access)
                InformationRequestRecurrenceDefinition(
                    requireNotNull(recurrenceRepository.findForOrigin(locked.request.id)),
                    InformationRequestETag.aggregateOf(locked.request),
                )
            }
        }
    }

    @Transactional
    fun createNextOccurrence(command: CreateNextInformationRequestOccurrenceCommand): InformationRequestSuccessorResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val recurrence = recurrenceRepository.findById(command.recurrenceId)
            ?.takeIf { it.originRequestId == command.requestId }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Recurrence not found"
            )
        val origin = gate.lock(recurrence.originRequestId)
        val series = lineageRepository.findForRecurrence(recurrence.id)
        val latest = InformationRequestRecurrenceSchedule.followedRequestId(recurrence, series) { occurrenceId ->
            requestRepository.findById(occurrenceId)?.state
        }
        val locked = if (latest == recurrence.originRequestId) origin else gate.lock(latest)
        val receipt = receipt(
            ResourceRef.informationRequest(recurrence.originRequestId),
            NEXT_OCCURRENCE_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(recurrence.id),
        )
        return successorService.run(locked, receipt, command.access, recurrence.originRequestId) {
            setOf(recurrence.originRequestId, latest).forEach { requestId ->
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_SUPERSEDE), requestId)
            }
            val sequence = series.size + 1
            if (recurrence.maximumOccurrences?.let { sequence > it } == true) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.RECURRENCE_COMPLETE,
                    "This recurrence has created every occurrence it allows",
                )
            }
            val due = dueAt(recurrence, sequence)
            if (due.isAfter(clock.instant()))
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.RECURRENCE_NOT_DUE,
                    "The next occurrence of this recurrence is not due",
                )
            }
            successorService.follow(
                locked,
                InformationRequestFollowUpSpec(
                    kind = InformationRequestLineageKind.RECURRENCE,
                    recurrenceId = recurrence.id,
                    recurrenceSequence = sequence,
                ),
                command.access,
                command.idempotencyKey,
            )
        }
    }

    @Transactional
    fun defineRefreshRule(command: DefineInformationRequestRefreshRuleCommand): InformationRequestRefreshRuleDefinition
    {
        InformationRequestFollowUpScheduleBounds.requireValid(command)
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val requirementKey = command.requirementKey.trim()
        val receipt = receipt(
            ResourceRef.informationRequest(command.requestId),
            DEFINE_REFRESH_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(requirementKey, command.leadDays),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                schedule(locked, command.access, command.precondition)
                requireRefreshableEvidence(locked, requirementKey)
                if (refreshRuleRepository.findForRequest(locked.request.id)
                        .any { it.requirementKey == requirementKey }
                ) {
                    throw InformationRequestLifecycleException(
                        InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                        "This requested document already has a refresh rule",
                    )
                }
                val rule = refreshRuleRepository.save(
                    InformationRequestRefreshRule().apply {
                        informationRequestId = locked.request.id
                        this.requirementKey = requirementKey
                        leadDays = command.leadDays
                        createdByPrincipalKind = command.access.principal.kind
                        createdByPrincipalId = command.access.principal.id
                        createdAt = Timestamp.from(clock.instant())
                    },
                )
                val requestETag = advance(locked)
                recordSchedule(
                    locked,
                    command.access,
                    command.idempotencyKey,
                    mapOf("refreshRuleId" to rule.id.toString())
                )
                CommandMutationResult(
                    InformationRequestRefreshRuleDefinition(rule, requestETag),
                    resultOf(locked, requestETag)
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> {
                authorizeReplay(locked, command.access)
                InformationRequestRefreshRuleDefinition(
                    refreshRuleRepository.findForRequest(locked.request.id)
                        .first { it.requirementKey == requirementKey },
                    InformationRequestETag.aggregateOf(locked.request),
                )
            }
        }
    }

    @Transactional
    fun refresh(command: RefreshInformationRequestCommand): InformationRequestSuccessorResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val rule = refreshRuleRepository.findById(command.refreshRuleId)
            ?.takeIf { it.informationRequestId == command.requestId }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Refresh rule not found"
            )
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef.informationRequest(command.requestId),
            REFRESH_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(rule.id),
        )
        return successorService.run(locked, receipt, command.access) {
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_SUPERSEDE), locked.request.id)
            val source = refreshRuleRepository.findRefreshSource(rule.informationRequestId, rule.requirementKey)
                ?.takeIf { InformationRequestRefreshSchedule.isDue(rule, it, clock.instant()) }
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REFRESH_NOT_DUE,
                    "The evidence this refresh rule renews is not yet within its lead time",
                )
            if (lineageRepository.existsRefresh(rule.id, source.packageId)) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REFRESH_ALREADY_CREATED,
                    "This submission's evidence has already been refreshed under this rule",
                )
            }
            successorService.follow(
                locked,
                InformationRequestRefreshSchedule.followUpOf(rule, source),
                command.access,
                command.idempotencyKey,
            ).also { refreshRuleRepository.clearRetry(rule) }
        }
    }

    fun dueAt(recurrence: InformationRequestRecurrence, sequence: Int): Instant =
        InformationRequestRecurrenceSchedule.dueAt(recurrence, sequence)

    private fun schedule(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        precondition: CommandPrecondition,
    )
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_SUPERSEDE), locked.request.id)
        gate.requireMutation(locked, InformationRequestMutation.SCHEDULE_FOLLOW_UP)
        gate.requireContinuation(locked, InformationRequestContinuationAction.SUPERSEDE)
        precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(locked.request))
    }

    private fun advance(locked: LockedInformationRequest): String {
        val request = locked.request
        request.aggregateRevision += 1
        request.updatedAt = Timestamp.from(clock.instant())
        requestRepository.update(request)
        return InformationRequestETag.aggregateOf(request)
    }

    private fun resultOf(locked: LockedInformationRequest, requestETag: String) =
        CommandResultReference(
            ResourceType.INFORMATION_REQUEST,
            locked.request.id,
            locked.request.aggregateRevision,
            requestETag
        )

    private fun authorizeReplay(locked: LockedInformationRequest, access: RequestAccessContext) {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW), locked.request.id)
    }

    private fun requireRefreshableEvidence(locked: LockedInformationRequest, requirementKey: String)
    {
        val documents = requirementRepository.findForRequest(locked.request.id).filter { requirement ->
            templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)?.let {
                it.requirementKey == requirementKey && it.requirementType == InformationRequestRequirementType.DOCUMENT
            } ?: false
        }
        if (documents.isEmpty())
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                "A refresh rule names a requested document of its own request",
            )
        }
        val capturesExpiry = documents.map { it.sourceTemplateBindingId }.distinct().any { bindingId ->
            evidencePolicies.forBinding(bindingId)
                ?.attributeRequirements
                ?.get(InformationRequestEvidenceCapturedAttribute.EXPIRY_DATE)
                ?.let { it != InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED }
                ?: false
        }
        if (!capturesExpiry) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REFRESH_EXPIRY_NOT_CAPTURED,
                "A refresh rule names a requested document whose expiry date is captured",
            )
        }
    }

    private fun recordSchedule(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        idempotencyKey: String,
        details: Map<String, String>,
    )
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = locked.request,
                fromState = locked.request.state,
                toState = locked.request.state,
                mutation = InformationRequestMutation.SCHEDULE_FOLLOW_UP,
                actor = InformationRequestAuditActor.of(access),
                auditIdempotencyKey = "information_request.follow_up|${locked.request.id}|$idempotencyKey",
                details = details,
            ),
        )
    }

    private fun receipt(
        resource: ResourceRef,
        operation: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        facts: List<Any?>,
    ) = CommandReceiptRequest(
        resource = resource,
        operation = operation,
        actor = CommandActorRef.principal(access.principal),
        idempotencyKey = idempotencyKey,
        requestFingerprint = CommandRequestFingerprint.of(operation, resource.id, *facts.toTypedArray()),
    )

    private companion object
    {
        const val DEFINE_RECURRENCE_OPERATION = "define-information-request-recurrence"
        const val NEXT_OCCURRENCE_OPERATION = "create-information-request-occurrence"
        const val DEFINE_REFRESH_OPERATION = "define-information-request-refresh-rule"
        const val REFRESH_OPERATION = "refresh-information-request"
    }
}
