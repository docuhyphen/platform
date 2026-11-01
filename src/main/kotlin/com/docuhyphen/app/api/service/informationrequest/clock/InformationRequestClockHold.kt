package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestRespondentWork
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestClockHold @Inject constructor(
    private val clockRepository: InformationRequestClockRepository,
    private val eventRepository: InformationRequestClockEventRepository,
    private val policies: InformationRequestClockPolicyService,
    private val recorder: InformationRequestClockRecorder,
    private val respondentWork: InformationRequestRespondentWork,
    private val clock: Clock,
) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun sync(locked: LockedInformationRequest) {
        val request = locked.request
        if (request.state.isTerminal) return
        val clocks = clockRepository.findForRequest(request.id)
        if (clocks.isEmpty()) return
        val now = clock.instant()
        if (respondentWork.completelySubmitted(request)) {
            clocks.filter { it.state == InformationRequestClockState.RUNNING }
                .sortedBy { it.id }
                .mapNotNull { clockRepository.findForUpdate(it.id) }
                .forEach { pause(request, it, now) }
        } else {
            clocks.filter { it.state == InformationRequestClockState.PAUSED && heldForSubmission(it) }
                .sortedBy { it.id }
                .mapNotNull { clockRepository.findForUpdate(it.id) }
                .forEach { resume(request, it, now) }
        }
    }

    private fun heldForSubmission(target: InformationRequestClock): Boolean =
        eventRepository.findForClock(target.id)
            .lastOrNull { it.eventKind == InformationRequestClockEventKind.PAUSED || it.eventKind == InformationRequestClockEventKind.RESUMED }
            ?.let {
                it.eventKind == InformationRequestClockEventKind.PAUSED &&
                        it.reasonCode == SUBMISSION_COMPLETE &&
                        it.recordedByPrincipalKind == SYSTEM.kind &&
                        it.recordedByPrincipalId == SYSTEM.id
            } ?: false

    private fun pause(request: InformationRequest, target: InformationRequestClock, now: Instant) {
        if (target.state != InformationRequestClockState.RUNNING) return
        val view = requireNotNull(policies.versionView(target.policyVersionId))
        val remaining = InformationRequestClockCalculator.elapsed(view.calendar, now, target.dueAt.toInstant())
        target.state = InformationRequestClockState.PAUSED
        target.remainingSeconds = remaining
        val event = recorder.append(
            target, InformationRequestClockEventKind.PAUSED, SYSTEM, now,
            mapOf("pausedAt" to now.toString(), "remainingSeconds" to remaining.toString()), SUBMISSION_COMPLETE,
        )
        recorder.save(target, view)
        recorder.transition(
            request, target, InformationRequestMutation.PAUSE_CLOCK, SYSTEM_ACTOR,
            "information_request.clock_hold|${target.id}|${event.eventNumber}", SUBMISSION_COMPLETE,
        )
    }

    private fun resume(request: InformationRequest, target: InformationRequestClock, now: Instant) {
        if (target.state != InformationRequestClockState.PAUSED) return
        val view = requireNotNull(policies.versionView(target.policyVersionId))
        val remaining = requireNotNull(target.remainingSeconds)
        target.state = InformationRequestClockState.RUNNING
        target.remainingSeconds = null
        target.dueAt = Timestamp.from(InformationRequestClockCalculator.advance(view.calendar, now, remaining))
        val event = recorder.append(
            target, InformationRequestClockEventKind.RESUMED, SYSTEM, now,
            mapOf("resumedAt" to now.toString(), "remainingSeconds" to remaining.toString()), RESPONSE_REOPENED,
        )
        recorder.save(target, view)
        recorder.transition(
            request, target, InformationRequestMutation.RESUME_CLOCK, SYSTEM_ACTOR,
            "information_request.clock_hold|${target.id}|${event.eventNumber}", RESPONSE_REOPENED,
        )
    }

    companion object {
        const val SUBMISSION_COMPLETE = "SUBMISSION_COMPLETE"
        const val RESPONSE_REOPENED = "RESPONSE_REOPENED"
        private val SYSTEM = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
        private val SYSTEM_ACTOR = InformationRequestAuditActor.system(SYSTEM)
    }
}
