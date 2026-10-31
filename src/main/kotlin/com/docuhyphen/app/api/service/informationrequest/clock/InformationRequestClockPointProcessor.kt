package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPoint
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestClockPointProcessor @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requestRepository: InformationRequestRepository,
    private val clockRepository: InformationRequestClockRepository,
    private val policies: InformationRequestClockPolicyService,
    private val recorder: InformationRequestClockRecorder,
    private val notices: InformationRequestClockNoticeHook,
    private val clockStopper: InformationRequestClockStopper,
)
{
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun process(clockId: UUID, now: Instant): Boolean
    {
        val candidate = clockRepository.findById(clockId) ?: return false
        val locked = gate.lock(candidate.informationRequestId)
        val clock = clockRepository.findForUpdate(clockId) ?: return false
        if (clock.state == InformationRequestClockState.STOPPED) return false
        val parentOpen = !locked.exchange.isDeleted &&
                locked.exchange.status in setOf(
            ExchangeStatus.INITIATED,
            ExchangeStatus.ACCEPTED_STARTED,
        )
        if (locked.request.state.isTerminal || !parentOpen)
        {
            recorder.stop(
                clock,
                SYSTEM,
                maxOf(finishedAt(locked, parentOpen, now), clock.startedAt.toInstant()),
                if (parentOpen) InformationRequestClockStopper.REQUEST_FINISHED else InformationRequestClockStopper.PARENT_FINISHED,
                mapOf("processedAt" to now.toString()),
            )
            return true
        }
        val pointAt = clock.nextPointAt?.toInstant()
        if (clock.state != InformationRequestClockState.RUNNING || pointAt == null || pointAt.isAfter(now)) return false
        val view = requireNotNull(policies.versionView(clock.policyVersionId))
        if (locked.request.state == InformationRequestState.DRAFT ||
            locked.exchange.status == ExchangeStatus.INITIATED ||
            !gate.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER).proceeds
        )
        {
            clock.nextPointAt = Timestamp.from(now.plus(DEFERRAL))

            clockRepository.update(clock)
            return true
        }
        val due = recorder.duePoints(clock, view).filter { !it.at.isAfter(now) }
        val latestReminder = due.filter { it.kind == InformationRequestClockEventKind.REMINDED }.maxByOrNull { it.at }
        for (point in due)
        {
            if (point.kind == InformationRequestClockEventKind.REMINDED && point != latestReminder) {
                recorder.append(
                    clock, point.kind, SYSTEM, now,
                    mapOf("pointAt" to point.at.toString(), "processedAt" to now.toString()),
                    REMINDER_SUPERSEDED,
                    point.ordinal,
                )
                continue
            }
            if (!record(locked, clock, view, point, now)) return true
        }
        recorder.save(clock, view)
        return true
    }

    private fun record(
        locked: LockedInformationRequest,
        clock: InformationRequestClock,
        view: InformationRequestClockPolicyVersionView,
        point: InformationRequestClockPoint,
        now: Instant,
    ): Boolean
    {
        val inputs = mapOf("pointAt" to point.at.toString(), "processedAt" to now.toString())
        when (point.kind)
        {
            InformationRequestClockEventKind.REMINDED ->
            {
                if (!now.isBefore(clock.dueAt.toInstant())) return true
                val event = recorder.append(clock, point.kind, SYSTEM, now, inputs, ordinal = point.ordinal)
                val owed = notices.reminded(locked.request, clock, event)
                recorder.transition(
                    locked.request, clock, InformationRequestMutation.RECORD_REMINDER, SYSTEM_ACTOR,
                    "information_request.clock_reminder|${clock.id}|${clock.dueCycle}|${point.ordinal}",
                    extra = mapOf("reminderOrdinal" to point.ordinal.toString(), NOTICE_COUNT to owed.toString()),
                )
            }

            InformationRequestClockEventKind.OVERDUE ->
            {
                clock.overdueAt = Timestamp.from(point.at)
                val event = recorder.append(clock, point.kind, SYSTEM, now, inputs)
                val owed = notices.overdue(locked.request, clock, event)
                recorder.transition(
                    locked.request, clock, InformationRequestMutation.RECORD_OVERDUE, SYSTEM_ACTOR,
                    "information_request.clock_overdue|${clock.id}|${clock.dueCycle}",
                    extra = mapOf(NOTICE_COUNT to owed.toString()),
                )
                if (view.version.dueEffect == InformationRequestClockDueEffect.EXPIRE_REQUEST)
                {
                    expire(locked, clock, now)
                    return false
                }
            }

            InformationRequestClockEventKind.ESCALATED ->
            {
                recorder.append(clock, point.kind, SYSTEM, now, inputs)
                recorder.transition(
                    locked.request, clock, InformationRequestMutation.RECORD_ESCALATION, SYSTEM_ACTOR,
                    "information_request.clock_escalation|${clock.id}|${clock.dueCycle}",
                )
            }

            else -> Unit
        }
        return true
    }

    private fun expire(locked: LockedInformationRequest, clock: InformationRequestClock, now: Instant)
    {
        val request = locked.request
        val fromState = request.state
        val nowStamp = Timestamp.from(now)
        request.state = InformationRequestState.EXPIRED
        request.expiredAt = nowStamp
        request.updatedAt = nowStamp
        request.aggregateRevision += 1
        requestRepository.update(request)
        recorder.append(
            clock,
            InformationRequestClockEventKind.EXPIRED,
            SYSTEM,
            now,
            mapOf("expiredAt" to now.toString())
        )
        recorder.expiryTransition(request, fromState, clock, SYSTEM_ACTOR)
        recorder.stop(clock, SYSTEM, now, "REQUEST_EXPIRED")
        clockStopper.stopForFinishedRequest(request, SYSTEM, now, InformationRequestClockStopper.REQUEST_FINISHED)
    }

    private fun finishedAt(locked: LockedInformationRequest, parentOpen: Boolean, now: Instant): Instant {
        val request = locked.request
        val requestFinishedAt = when (request.state) {
            InformationRequestState.CLOSED -> request.closedAt
            InformationRequestState.CANCELLED -> request.cancelledAt
            InformationRequestState.SUPERSEDED -> request.supersededAt
            InformationRequestState.EXPIRED -> request.expiredAt
            else -> null
        }
        val parentFinishedAt = locked.exchange.endDate.takeUnless { parentOpen }
        return listOfNotNull(requestFinishedAt, parentFinishedAt)
            .minOfOrNull { it.toInstant() }
            ?.let { minOf(it, now) }
            ?: now
    }

    private companion object
    {
        val SYSTEM = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
        val SYSTEM_ACTOR = InformationRequestAuditActor.system(SYSTEM)
        const val NOTICE_COUNT = "noticeIntentCount"
        const val REMINDER_SUPERSEDED = "REMINDER_SUPERSEDED"
        val DEFERRAL: Duration = Duration.ofHours(1)
    }
}
