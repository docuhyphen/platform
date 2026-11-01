package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestClockIssueRebaser @Inject constructor(
    private val clockRepository: InformationRequestClockRepository,
    private val policies: InformationRequestClockPolicyService,
    private val recorder: InformationRequestClockRecorder,
) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun rebaseOnIssue(request: InformationRequest, issuedAt: Instant, actor: PrincipalRef) {
        clockRepository.findForRequest(request.id)
            .filter { it.state != InformationRequestClockState.STOPPED && startedWithoutReceivedTime(it, issuedAt) }
            .sortedBy { it.id }
            .mapNotNull { clockRepository.findForUpdate(it.id) }
            .forEach { clock -> rebase(clock, issuedAt, actor, REQUEST_ISSUED) }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun rebaseOnAcceptance(request: InformationRequest, acceptedAt: Instant, actor: PrincipalRef) {
        clockRepository.findForRequest(request.id)
            .filter {
                it.state != InformationRequestClockState.STOPPED &&
                        receivedByDefault(it, request) &&
                        it.receivedAt.toInstant().isBefore(acceptedAt)
            }
            .sortedBy { it.id }
            .mapNotNull { clockRepository.findForUpdate(it.id) }
            .forEach { clock -> rebase(clock, acceptedAt, actor, EXCHANGE_ACCEPTED) }
    }

    private fun startedWithoutReceivedTime(clock: InformationRequestClock, issuedAt: Instant): Boolean =
        clock.receivedAt == clock.startedAt && clock.receivedAt.toInstant().isBefore(issuedAt)

    private fun receivedByDefault(clock: InformationRequestClock, request: InformationRequest): Boolean =
        clock.receivedAt.toInstant() == clock.startedAt.toInstant() ||
                clock.receivedAt.toInstant() == request.issuedAt?.toInstant()

    private fun rebase(clock: InformationRequestClock, from: Instant, actor: PrincipalRef, reasonCode: String) {
        val view = requireNotNull(policies.versionView(clock.policyVersionId))
        val allotted = InformationRequestClockCalculator.elapsed(
            view.calendar,
            clock.receivedAt.toInstant(),
            clock.dueAt.toInstant()
        )
        if (clock.state == InformationRequestClockState.PAUSED) clock.remainingSeconds = allotted
        clock.dueAt = Timestamp.from(InformationRequestClockCalculator.advance(view.calendar, from, allotted))
        recorder.append(
            clock,
            InformationRequestClockEventKind.RESUMED,
            actor,
            from,
            mapOf("resumedAt" to from.toString(), "remainingSeconds" to allotted.toString()),
            reasonCode,
        )
        recorder.save(clock, view)
    }

    private companion object {
        const val REQUEST_ISSUED = "REQUEST_ISSUED"
        const val EXCHANGE_ACCEPTED = "EXCHANGE_ACCEPTED"
    }
}
