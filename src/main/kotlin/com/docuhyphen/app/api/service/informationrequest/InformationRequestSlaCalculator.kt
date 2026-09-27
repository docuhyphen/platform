package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaClock
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStatus
import java.time.Instant

object InformationRequestSlaCalculator
{
    private val URGENCY = listOf(
        InformationRequestSlaStatus.OVERDUE,
        InformationRequestSlaStatus.DUE_SOON,
        InformationRequestSlaStatus.PAUSED,
        InformationRequestSlaStatus.ON_TRACK,
        InformationRequestSlaStatus.MET,
    )

    fun standing(clocks: List<InformationRequestSlaClock>, now: Instant): InformationRequestSlaStanding
    {
        if (clocks.isEmpty()) return InformationRequestSlaStanding(InformationRequestSlaStatus.NO_CLOCK, null, 0, 0)
        return InformationRequestSlaStanding(
            status = clocks.map { statusOf(it, now) }.minBy(URGENCY::indexOf),
            nearestDueAt = clocks.filter { it.clock.state == InformationRequestClockState.RUNNING }.minOfOrNull { it.clock.dueAt.toInstant() },
            reminderCount = clocks.sumOf { input -> input.events.count { it.eventKind == InformationRequestClockEventKind.REMINDED } },
            openEscalationCount = clocks.filter { it.clock.state != InformationRequestClockState.STOPPED }.sumOf { input ->
                input.events.count { it.eventKind == InformationRequestClockEventKind.ESCALATED && it.dueCycle == input.clock.dueCycle }
            },
        )
    }

    fun statusOf(input: InformationRequestSlaClock, now: Instant): InformationRequestSlaStatus
    {
        val clock = input.clock
        val due = clock.dueAt.toInstant()
        return when (clock.state)
        {
            InformationRequestClockState.STOPPED ->
                if (requireNotNull(clock.stoppedAt).toInstant().isAfter(due)) InformationRequestSlaStatus.OVERDUE else InformationRequestSlaStatus.MET
            InformationRequestClockState.PAUSED ->
                if (clock.overdueAt != null) InformationRequestSlaStatus.OVERDUE else InformationRequestSlaStatus.PAUSED
            InformationRequestClockState.RUNNING -> when
            {
                clock.overdueAt != null || !now.isBefore(due) -> InformationRequestSlaStatus.OVERDUE
                input.dueSoonFrom != null && !now.isBefore(input.dueSoonFrom) -> InformationRequestSlaStatus.DUE_SOON
                else -> InformationRequestSlaStatus.ON_TRACK
            }
        }
    }
}
