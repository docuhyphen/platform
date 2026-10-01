package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockEvent
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaClock
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestSlaCalculatorTest
{
    private val now: Instant = Instant.parse("2026-09-26T10:00:00Z")

    @Test
    fun `a request without clocks has no service level`()
    {
        val standing = InformationRequestSlaCalculator.standing(emptyList(), now)

        assertEquals(InformationRequestSlaStatus.NO_CLOCK, standing.status)
        assertNull(standing.nearestDueAt)
    }

    @Test
    fun `a running clock is on track, due soon from its first reminder point, and overdue at its due instant`()
    {
        val due = now.plusSeconds(HOUR)
        val onTrack = slaClock(running(due), dueSoonFrom = now.plusSeconds(MINUTE))
        val dueSoon = slaClock(running(due), dueSoonFrom = now)
        val withoutReminders = slaClock(running(due), dueSoonFrom = null)
        val overdue = slaClock(running(now), dueSoonFrom = now.minusSeconds(HOUR))

        assertEquals(InformationRequestSlaStatus.ON_TRACK, InformationRequestSlaCalculator.statusOf(onTrack, now))
        assertEquals(InformationRequestSlaStatus.DUE_SOON, InformationRequestSlaCalculator.statusOf(dueSoon, now))
        assertEquals(InformationRequestSlaStatus.ON_TRACK, InformationRequestSlaCalculator.statusOf(withoutReminders, now))
        assertEquals(InformationRequestSlaStatus.OVERDUE, InformationRequestSlaCalculator.statusOf(overdue, now))
    }

    @Test
    fun `a recorded overdue point stays overdue, a paused clock is paused, and a stopped clock is met only by its due instant`()
    {
        val recordedOverdue = running(now.plusSeconds(HOUR)).apply { overdueAt = Timestamp.from(now.minusSeconds(MINUTE)) }
        val paused = running(now.minusSeconds(HOUR)).apply { state = InformationRequestClockState.PAUSED }
        val stoppedInTime = stopped(due = now, stoppedAt = now.minusSeconds(MINUTE))
        val stoppedLate = stopped(due = now.minusSeconds(HOUR), stoppedAt = now)

        assertEquals(InformationRequestSlaStatus.OVERDUE, InformationRequestSlaCalculator.statusOf(slaClock(recordedOverdue), now))
        assertEquals(InformationRequestSlaStatus.PAUSED, InformationRequestSlaCalculator.statusOf(slaClock(paused), now))
        assertEquals(InformationRequestSlaStatus.MET, InformationRequestSlaCalculator.statusOf(slaClock(stoppedInTime), now))
        assertEquals(InformationRequestSlaStatus.OVERDUE, InformationRequestSlaCalculator.statusOf(slaClock(stoppedLate), now))
    }

    @Test
    fun `a request takes its most urgent clock status, its nearest running due instant, and counts its reminders and open escalations`()
    {
        val nearest = running(now.plusSeconds(2 * HOUR))
        val later = running(now.plusSeconds(5 * HOUR))
        val finished = stopped(due = now.plusSeconds(HOUR), stoppedAt = now.minusSeconds(HOUR))
        val escalatedEarlierCycle = running(now.plusSeconds(3 * HOUR)).apply { dueCycle = 1 }
        val standing = InformationRequestSlaCalculator.standing(
            listOf(
                slaClock(nearest, dueSoonFrom = now.minusSeconds(MINUTE), events = listOf(event(nearest, InformationRequestClockEventKind.REMINDED))),
                slaClock(later, events = listOf(event(later, InformationRequestClockEventKind.ESCALATED))),
                slaClock(finished, events = listOf(event(finished, InformationRequestClockEventKind.ESCALATED))),
                slaClock(
                    escalatedEarlierCycle,
                    events = listOf(
                        event(escalatedEarlierCycle, InformationRequestClockEventKind.REMINDED, cycle = 0),
                        event(escalatedEarlierCycle, InformationRequestClockEventKind.ESCALATED, cycle = 0),
                    ),
                ),
            ),
            now,
        )

        assertEquals(InformationRequestSlaStatus.DUE_SOON, standing.status)
        assertEquals(nearest.dueAt.toInstant(), standing.nearestDueAt)
        assertEquals(2, standing.reminderCount)
        assertEquals(1, standing.openEscalationCount)
        assertEquals(
            InformationRequestSlaStatus.MET,
            InformationRequestSlaCalculator.standing(listOf(slaClock(finished)), now).status,
        )
    }

    private fun slaClock(
        clock: InformationRequestClock,
        dueSoonFrom: Instant? = null,
        events: List<InformationRequestClockEvent> = emptyList(),
    ) = InformationRequestSlaClock(clock, events, dueSoonFrom)

    private fun running(due: Instant) = InformationRequestClock().apply {
        informationRequestId = REQUEST_ID
        clockKey = "response-${UUID.randomUUID()}"
        policyVersionId = UUID.randomUUID()
        receivedAt = Timestamp.from(now.minusSeconds(DAY))
        dueAt = Timestamp.from(due)
        startedByPrincipalId = UUID.randomUUID()
        startedAt = Timestamp.from(now.minusSeconds(DAY))
    }

    private fun stopped(due: Instant, stoppedAt: Instant) = running(due).apply {
        state = InformationRequestClockState.STOPPED
        this.stoppedAt = Timestamp.from(stoppedAt)
    }

    private fun event(clock: InformationRequestClock, kind: InformationRequestClockEventKind, cycle: Int = clock.dueCycle) =
        InformationRequestClockEvent().apply {
            clockId = clock.id
            eventKind = kind
            dueCycle = cycle
        }

    private companion object
    {
        const val MINUTE = 60L
        const val HOUR = 3600L
        const val DAY = 86_400L
        val REQUEST_ID: UUID = UUID.randomUUID()
    }
}
