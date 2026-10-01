package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockEvent
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPoint
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestClockRecorder @Inject constructor(
    private val clockRepository: InformationRequestClockRepository,
    private val eventRepository: InformationRequestClockEventRepository,
    private val transitionHistory: InformationRequestTransitionHistoryService,
)
{
    @Suppress("LongParameterList")
    fun append(
        clock: InformationRequestClock,
        kind: InformationRequestClockEventKind,
        actor: PrincipalRef,
        at: Instant,
        inputs: Map<String, String>,
        reasonCode: String? = null,
        ordinal: Int? = null,
    ): InformationRequestClockEvent =
        eventRepository.save(
            InformationRequestClockEvent().apply {
                clockId = clock.id
                informationRequestId = clock.informationRequestId
                eventNumber = eventRepository.nextEventNumber(clock.id)
                eventKind = kind
                dueCycle = clock.dueCycle
                pointOrdinal = ordinal
                this.reasonCode = reasonCode?.trim()?.ifBlank { null }
                inputsJson = JsonObject(inputs.mapValues { JsonPrimitive(it.value) }).toString()
                dueAt = clock.dueAt
                occurredAt = Timestamp.from(at)
                recordedByPrincipalKind = actor.kind
                recordedByPrincipalId = actor.id
            },
        )

    fun save(clock: InformationRequestClock, view: InformationRequestClockPolicyVersionView): InformationRequestClock
    {
        clock.nextPointAt = nextPointAt(clock, view)?.let(Timestamp::from)
        clock.clockRevision += 1
        return clockRepository.update(clock)
    }

    fun stop(clock: InformationRequestClock, actor: PrincipalRef, at: Instant, reasonCode: String)
    {
        clock.state = InformationRequestClockState.STOPPED
        clock.remainingSeconds = null
        clock.stoppedAt = Timestamp.from(at)
        clock.nextPointAt = null
        append(clock, InformationRequestClockEventKind.STOPPED, actor, at, mapOf("stoppedAt" to at.toString()), reasonCode)
        clock.clockRevision += 1
        clockRepository.update(clock)
    }

    @Suppress("LongParameterList")
    fun transition(
        request: InformationRequest,
        clock: InformationRequestClock,
        mutation: InformationRequestMutation,
        actor: PrincipalRef,
        idempotencyKey: String,
        reasonCode: String? = null,
        extra: Map<String, String> = emptyMap(),
    )
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                reasonCode = reasonCode,
                idempotencyKey = idempotencyKey,
                details = mapOf(
                    "clockId" to clock.id.toString(),
                    "clockKey" to clock.clockKey,
                    "dueAt" to clock.dueAt.toInstant().toString(),
                ) + extra,
            ),
        )
    }

    fun expiryTransition(request: InformationRequest, fromState: InformationRequestState, clock: InformationRequestClock, actor: PrincipalRef)
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = fromState,
                toState = request.state,
                mutation = InformationRequestMutation.EXPIRE,
                actor = actor,
                reasonCode = "CLOCK_DUE",
                idempotencyKey = "information_request.clock_expiry|${clock.id}",
                details = mapOf(
                    "clockId" to clock.id.toString(),
                    "clockKey" to clock.clockKey,
                    "dueAt" to clock.dueAt.toInstant().toString(),
                ),
            ),
        )
    }

    fun duePoints(clock: InformationRequestClock, view: InformationRequestClockPolicyVersionView): List<InformationRequestClockPoint>
    {
        if (clock.state != InformationRequestClockState.RUNNING) return emptyList()
        val recorded = eventRepository.findForClock(clock.id)
            .filter { it.dueCycle == clock.dueCycle }
            .map { it.eventKind to it.pointOrdinal }
            .toSet()
        val due = clock.dueAt.toInstant()
        val points = mutableListOf<InformationRequestClockPoint>()
        if ((InformationRequestClockEventKind.OVERDUE to null) !in recorded)
        {
            view.reminderMinutesBeforeDue.forEachIndexed { index, minutes ->
                val ordinal = index + 1
                if ((InformationRequestClockEventKind.REMINDED to ordinal) in recorded) return@forEachIndexed
                val at = InformationRequestClockCalculator.retreat(view.calendar, due, minutes * SECONDS_PER_MINUTE)
                if (!at.isBefore(clock.receivedAt.toInstant())) points += InformationRequestClockPoint(InformationRequestClockEventKind.REMINDED, at, ordinal)
            }
            points += InformationRequestClockPoint(InformationRequestClockEventKind.OVERDUE, due, null)
        }
        view.version.escalationAfterMinutes?.let { minutes ->
            if ((InformationRequestClockEventKind.ESCALATED to null) !in recorded)
            {
                val at = InformationRequestClockCalculator.advance(view.calendar, due, minutes * SECONDS_PER_MINUTE)
                points += InformationRequestClockPoint(InformationRequestClockEventKind.ESCALATED, at, null)
            }
        }
        return points.sortedWith(compareBy<InformationRequestClockPoint>({ it.at }, { it.kind.ordinal }))
    }

    fun nextPointAt(clock: InformationRequestClock, view: InformationRequestClockPolicyVersionView): Instant? =
        duePoints(clock, view).minOfOrNull { it.at }

    private companion object
    {
        const val SECONDS_PER_MINUTE = 60L
    }
}
