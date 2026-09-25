package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestRefreshRule
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestFollowUpSpec
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorResult
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RefreshInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
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
import java.time.Instant
import java.time.ZoneOffset

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
    private val clock: Clock,
)
{
    @Transactional
    fun defineRecurrence(command: DefineInformationRequestRecurrenceCommand): InformationRequestRecurrence
    {
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef.informationRequest(command.requestId),
            DEFINE_RECURRENCE_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(command.intervalUnit, command.intervalCount, command.firstDueAt, command.maximumOccurrences ?: ""),
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
                recordSchedule(locked, command.access, command.idempotencyKey, mapOf("recurrenceId" to recurrence.id.toString()))
                CommandMutationResult(recurrence, CommandResultReference(ResourceType.INFORMATION_REQUEST, locked.request.id))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> requireNotNull(recurrenceRepository.findForOrigin(locked.request.id))
        }
    }

    @Transactional
    fun createNextOccurrence(command: CreateNextInformationRequestOccurrenceCommand): InformationRequestSuccessorResult
    {
        val recurrence = recurrenceRepository.findById(command.recurrenceId)
            ?.takeIf { it.originRequestId == command.requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Recurrence not found")
        val origin = gate.lock(recurrence.originRequestId)
        val series = lineageRepository.findForRecurrence(recurrence.id)
        val latest = series.lastOrNull()?.successorRequestId ?: recurrence.originRequestId
        val locked = if (latest == recurrence.originRequestId) origin else gate.lock(latest)
        val receipt = receipt(
            ResourceRef.informationRequest(recurrence.originRequestId),
            NEXT_OCCURRENCE_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(recurrence.id),
        )
        return successorService.run(locked, receipt, command.access) {
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_SUPERSEDE), recurrence.originRequestId)
            val sequence = series.size + 1
            val due = dueAt(recurrence, sequence)
            if ((recurrence.maximumOccurrences?.let { sequence > it } ?: false) || due.isAfter(clock.instant()))
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
    fun defineRefreshRule(command: DefineInformationRequestRefreshRuleCommand): InformationRequestRefreshRule
    {
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
                recordSchedule(locked, command.access, command.idempotencyKey, mapOf("refreshRuleId" to rule.id.toString()))
                CommandMutationResult(rule, CommandResultReference(ResourceType.INFORMATION_REQUEST, locked.request.id))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
                refreshRuleRepository.findForRequest(locked.request.id).first { it.requirementKey == requirementKey }
        }
    }

    @Transactional
    fun refresh(command: RefreshInformationRequestCommand): InformationRequestSuccessorResult
    {
        val rule = refreshRuleRepository.findById(command.refreshRuleId)
            ?.takeIf { it.informationRequestId == command.requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Refresh rule not found")
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
            successorService.follow(
                locked,
                InformationRequestFollowUpSpec(kind = InformationRequestLineageKind.REFRESH, refreshRuleId = rule.id),
                command.access,
                command.idempotencyKey,
            )
        }
    }

    fun dueAt(recurrence: InformationRequestRecurrence, sequence: Int): Instant
    {
        val steps = (sequence - 1).toLong() * recurrence.intervalCount
        val first = recurrence.firstDueAt.toInstant().atZone(ZoneOffset.UTC)
        return when (recurrence.intervalUnit)
        {
            InformationRequestRecurrenceUnit.DAY -> first.plusDays(steps)
            InformationRequestRecurrenceUnit.WEEK -> first.plusWeeks(steps)
            InformationRequestRecurrenceUnit.MONTH -> first.plusMonths(steps)
            InformationRequestRecurrenceUnit.YEAR -> first.plusYears(steps)
        }.toInstant()
    }

    private fun schedule(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        precondition: CommandPrecondition,
    )
    {
        gate.requireMutation(locked, InformationRequestMutation.SCHEDULE_FOLLOW_UP)
        gate.requireContinuationEntitlement(locked)
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_SUPERSEDE), locked.request.id)
        precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(locked.request))
    }

    private fun requireRefreshableEvidence(locked: LockedInformationRequest, requirementKey: String)
    {
        val refreshable = requirementRepository.findForRequest(locked.request.id).any { requirement ->
            templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)?.let {
                it.requirementKey == requirementKey && it.requirementType == InformationRequestRequirementType.DOCUMENT
            } ?: false
        }
        if (!refreshable)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                "A refresh rule names a requested document of its own request",
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
                actor = access.principal,
                idempotencyKey = "information_request.follow_up|${locked.request.id}|$idempotencyKey",
                details = details,
            ),
        )
    }

    private fun receipt(
        resource: ResourceRef,
        operation: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        facts: List<Any>,
    ) = CommandReceiptRequest(
        resource = resource,
        operation = operation,
        actor = CommandActorRef.principal(access.principal),
        idempotencyKey = idempotencyKey,
        requestFingerprint = CommandRequestFingerprint.sha256Hex((listOf(operation, resource.id) + facts).joinToString("|")),
    )

    private companion object
    {
        const val DEFINE_RECURRENCE_OPERATION = "define-information-request-recurrence"
        const val NEXT_OCCURRENCE_OPERATION = "create-information-request-occurrence"
        const val DEFINE_REFRESH_OPERATION = "define-information-request-refresh-rule"
        const val REFRESH_OPERATION = "refresh-information-request"
    }
}
