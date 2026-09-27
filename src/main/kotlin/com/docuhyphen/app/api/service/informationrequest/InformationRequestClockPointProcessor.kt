package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockPoint
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestClockPointProcessor @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requestRepository: InformationRequestRepository,
    private val clockRepository: InformationRequestClockRepository,
    private val policies: InformationRequestClockPolicyService,
    private val recorder: InformationRequestClockRecorder,
    private val notices: InformationRequestClockNoticeHook,
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
            recorder.stop(clock, SYSTEM, now, if (parentOpen) "REQUEST_FINISHED" else "PARENT_FINISHED")
            return true
        }
        val pointAt = clock.nextPointAt?.toInstant()
        if (clock.state != InformationRequestClockState.RUNNING || pointAt == null || pointAt.isAfter(now)) return false
        val view = requireNotNull(policies.versionView(clock.policyVersionId))
        if (locked.request.state == InformationRequestState.DRAFT)
        {
            clock.nextPointAt = Timestamp.from(now.plus(DRAFT_DEFERRAL))
            clock.clockRevision += 1
            clockRepository.update(clock)
            return true
        }
        for (point in recorder.duePoints(clock, view).filter { !it.at.isAfter(now) })
        {
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
                    locked.request, clock, InformationRequestMutation.RECORD_REMINDER, SYSTEM,
                    "information_request.clock_reminder|${clock.id}|${clock.dueCycle}|${point.ordinal}",
                    extra = mapOf("reminderOrdinal" to point.ordinal.toString(), NOTICE_COUNT to owed.toString()),
                )
            }
            InformationRequestClockEventKind.OVERDUE ->
            {
                clock.overdueAt = Timestamp.from(now)
                val event = recorder.append(clock, point.kind, SYSTEM, now, inputs)
                val owed = notices.overdue(locked.request, clock, event)
                recorder.transition(
                    locked.request, clock, InformationRequestMutation.RECORD_OVERDUE, SYSTEM,
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
                    locked.request, clock, InformationRequestMutation.RECORD_ESCALATION, SYSTEM,
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
        recorder.append(clock, InformationRequestClockEventKind.EXPIRED, SYSTEM, now, mapOf("expiredAt" to now.toString()))
        recorder.expiryTransition(request, fromState, clock, SYSTEM)
        recorder.stop(clock, SYSTEM, now, "REQUEST_EXPIRED")
    }

    private companion object
    {
        val SYSTEM = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
        const val NOTICE_COUNT = "noticeIntentCount"
        val DRAFT_DEFERRAL: Duration = Duration.ofHours(1)
    }
}
