package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.StartInformationRequestClockCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
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
class InformationRequestClockService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val clockRepository: InformationRequestClockRepository,
    private val eventRepository: InformationRequestClockEventRepository,
    private val policies: InformationRequestClockPolicyService,
    private val recorder: InformationRequestClockRecorder,
    private val commandReceiptService: CommandReceiptService,
    private val clock: Clock,
)
{
    @Transactional
    fun start(command: StartInformationRequestClockCommand): InformationRequestClockView
    {
        val locked = gate.lock(command.requestId)
        val key = command.clockKey.trim().lowercase()
        val receipt = receipt(
            command.requestId, START_OPERATION, command.access, command.idempotencyKey,
            listOf(key, command.policyVersionId, command.urgency, command.receivedAt ?: ""),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                gate.requireMutation(locked, InformationRequestMutation.START_CLOCK)
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_CLOCKS), command.requestId)
                val view = availableVersion(locked, command.policyVersionId)
                if (!KEY.matches(key)) throw InformationRequestCommandRequestException("A clock key uses lowercase letters, digits, dots, dashes, or underscores")
                if (clockRepository.findForRequest(command.requestId).any { it.clockKey == key })
                {
                    throw InformationRequestLifecycleException(InformationRequestErrorCatalog.CLOCK_KEY_TAKEN, "This request already has a clock with this key")
                }
                val now = clock.instant()
                val received = command.receivedAt ?: locked.request.issuedAt?.toInstant() ?: now
                val minutes = when (command.urgency)
                {
                    InformationRequestClockUrgency.STANDARD -> view.version.standardDurationMinutes
                    InformationRequestClockUrgency.URGENT -> view.version.urgentDurationMinutes
                }
                val started = InformationRequestClock().apply {
                    informationRequestId = command.requestId
                    clockKey = key
                    policyVersionId = view.version.id
                    urgency = command.urgency
                    receivedAt = Timestamp.from(received)
                    state = InformationRequestClockState.RUNNING
                    dueAt = Timestamp.from(InformationRequestClockCalculator.advance(view.calendar, received, minutes * SECONDS_PER_MINUTE))
                    startedByPrincipalKind = command.access.principal.kind
                    startedByPrincipalId = command.access.principal.id
                    startedAt = Timestamp.from(now)
                }
                clockRepository.save(started)
                recorder.append(
                    started, InformationRequestClockEventKind.STARTED, command.access.principal, now,
                    mapOf(
                        "policyVersionId" to view.version.id.toString(),
                        "clockType" to view.version.clockType.name,
                        "businessTimezone" to view.version.businessTimezone,
                        "urgency" to command.urgency.name,
                        "budgetMinutes" to minutes.toString(),
                        "receivedAt" to received.toString(),
                    ),
                )
                recorder.save(started, view)
                recorder.transition(
                    locked.request, started, InformationRequestMutation.START_CLOCK, command.access.principal,
                    "information_request.clock_start|${started.id}",
                )
                CommandMutationResult(started, CommandResultReference(ResourceType.INFORMATION_REQUEST, command.requestId))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> view(decision.response)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_CLOCKS), command.requestId)
                view(requireNotNull(clockRepository.findForRequest(command.requestId).firstOrNull { it.clockKey == key }))
            }
        }
    }

    @Transactional
    fun change(command: ChangeInformationRequestClockCommand): InformationRequestClockView
    {
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            command.requestId, CHANGE_OPERATION, command.access, command.idempotencyKey,
            listOf(command.clockId, command.change, command.extensionMinutes ?: "", command.reasonCode.orEmpty()),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val mutation = when (command.change)
                {
                    InformationRequestClockChange.PAUSE -> InformationRequestMutation.PAUSE_CLOCK
                    InformationRequestClockChange.RESUME -> InformationRequestMutation.RESUME_CLOCK
                    InformationRequestClockChange.EXTEND -> InformationRequestMutation.EXTEND_CLOCK
                }
                gate.requireMutation(locked, mutation)
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_CLOCKS), command.requestId)
                val reason = command.reasonCode?.trim()?.ifBlank { null }
                    ?: throw InformationRequestCommandRequestException("A clock change states its reason")
                val target = clockRepository.findForUpdate(command.clockId)?.takeIf { it.informationRequestId == command.requestId }
                    ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Request clock not found")
                command.precondition.requireSatisfiedBy(InformationRequestETag.clockOf(target))
                val view = requireNotNull(policies.versionView(target.policyVersionId))
                val now = clock.instant()
                when (command.change)
                {
                    InformationRequestClockChange.PAUSE -> pause(target, view, reason, command, now)
                    InformationRequestClockChange.RESUME -> resume(target, view, reason, command, now)
                    InformationRequestClockChange.EXTEND -> extend(target, view, reason, command, now)
                }
                recorder.save(target, view)
                recorder.transition(
                    locked.request, target, mutation, command.access.principal,
                    "information_request.clock_change|${target.id}|${command.idempotencyKey}", reason,
                )
                CommandMutationResult(target, CommandResultReference(ResourceType.INFORMATION_REQUEST, command.requestId))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> view(decision.response)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_CLOCKS), command.requestId)
                view(requireNotNull(clockRepository.findById(command.clockId)))
            }
        }
    }

    fun clocks(requestId: UUID, access: RequestAccessContext): List<InformationRequestClockView>
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS), requestId)
        return clockRepository.findForRequest(requestId).map(::view)
    }

    private fun pause(
        target: InformationRequestClock,
        view: InformationRequestClockPolicyVersionView,
        reason: String,
        command: ChangeInformationRequestClockCommand,
        now: java.time.Instant,
    )
    {
        requireState(target, InformationRequestClockState.RUNNING)
        val remaining = InformationRequestClockCalculator.elapsed(view.calendar, now, target.dueAt.toInstant())
        target.state = InformationRequestClockState.PAUSED
        target.remainingSeconds = remaining
        recorder.append(
            target, InformationRequestClockEventKind.PAUSED, command.access.principal, now,
            mapOf("pausedAt" to now.toString(), "remainingSeconds" to remaining.toString()), reason,
        )
    }

    private fun resume(
        target: InformationRequestClock,
        view: InformationRequestClockPolicyVersionView,
        reason: String,
        command: ChangeInformationRequestClockCommand,
        now: java.time.Instant,
    )
    {
        requireState(target, InformationRequestClockState.PAUSED)
        val remaining = requireNotNull(target.remainingSeconds)
        target.state = InformationRequestClockState.RUNNING
        target.remainingSeconds = null
        target.dueAt = Timestamp.from(InformationRequestClockCalculator.advance(view.calendar, now, remaining))
        recorder.append(
            target, InformationRequestClockEventKind.RESUMED, command.access.principal, now,
            mapOf("resumedAt" to now.toString(), "remainingSeconds" to remaining.toString()), reason,
        )
    }

    private fun extend(
        target: InformationRequestClock,
        view: InformationRequestClockPolicyVersionView,
        reason: String,
        command: ChangeInformationRequestClockCommand,
        now: java.time.Instant,
    )
    {
        val minutes = command.extensionMinutes?.takeIf { it > 0 }
            ?: throw InformationRequestCommandRequestException("A clock extension adds a positive number of minutes")
        if (target.state == InformationRequestClockState.STOPPED)
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.CLOCK_STATE_INVALID, "A stopped clock cannot be extended")
        }
        val previousDue = target.dueAt.toInstant()
        val seconds = minutes * SECONDS_PER_MINUTE
        target.dueCycle += 1
        target.overdueAt = null
        if (target.state == InformationRequestClockState.RUNNING)
            target.dueAt = Timestamp.from(InformationRequestClockCalculator.advance(view.calendar, previousDue, seconds))
        else
            target.remainingSeconds = requireNotNull(target.remainingSeconds) + seconds
        recorder.append(
            target, InformationRequestClockEventKind.EXTENDED, command.access.principal, now,
            mapOf("extensionMinutes" to minutes.toString(), "previousDueAt" to previousDue.toString()), reason,
        )
    }

    private fun requireState(target: InformationRequestClock, expected: InformationRequestClockState)
    {
        if (target.state != expected)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CLOCK_STATE_INVALID,
                "This clock is ${target.state.name.lowercase()}",
            )
        }
    }

    private fun availableVersion(locked: LockedInformationRequest, versionId: UUID): InformationRequestClockPolicyVersionView
    {
        val owner = policies.ownerOfVersion(versionId)
        val view = policies.versionView(versionId)
        if (view == null || owner != InformationRequestOwnerRef.of(locked.request))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CLOCK_POLICY_UNAVAILABLE,
                "The clock policy version is not available to this request's owner",
            )
        }
        return view
    }

    private fun view(target: InformationRequestClock): InformationRequestClockView =
        InformationRequestClockView(
            clock = target,
            policyVersion = requireNotNull(policies.versionView(target.policyVersionId)),
            events = eventRepository.findForClock(target.id),
            clockETag = InformationRequestETag.clockOf(target),
        )

    private fun receipt(
        requestId: UUID,
        operation: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        facts: List<Any>,
    ) = CommandReceiptRequest(
        resource = ResourceRef.informationRequest(requestId),
        operation = operation,
        actor = CommandActorRef.principal(access.principal),
        idempotencyKey = idempotencyKey,
        requestFingerprint = CommandRequestFingerprint.sha256Hex((listOf(operation, requestId) + facts).joinToString("|")),
    )

    private companion object
    {
        const val START_OPERATION = "start-information-request-clock"
        const val CHANGE_OPERATION = "change-information-request-clock"
        const val SECONDS_PER_MINUTE = 60L
        val KEY = Regex("^[a-z0-9][a-z0-9._-]*$")
    }
}
