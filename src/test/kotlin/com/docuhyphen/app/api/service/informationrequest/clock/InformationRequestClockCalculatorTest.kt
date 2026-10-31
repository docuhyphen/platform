package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockCalendar
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestWorkingPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class InformationRequestClockCalculatorTest
{
    private val johannesburg = ZoneId.of("Africa/Johannesburg")
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `a calendar clock counts every elapsed second`()
    {
        val calendar = InformationRequestClockCalendar(InformationRequestClockType.CALENDAR, johannesburg, emptyList(), emptySet())
        val received = Instant.parse("2026-09-25T14:00:00Z")

        assertEquals(Instant.parse("2026-09-27T14:00:00Z"), InformationRequestClockCalculator.advance(calendar, received, 2 * DAY))
        assertEquals(DAY, InformationRequestClockCalculator.elapsed(calendar, received, received.plusSeconds(DAY)))
        assertEquals(received, InformationRequestClockCalculator.retreat(calendar, received.plusSeconds(HOUR), HOUR))
    }

    @Test
    fun `a business clock counts only working hours in its own timezone and skips weekends`()
    {
        val calendar = weekdays(johannesburg)
        val fridayAfternoon = Instant.parse("2026-09-25T14:00:00Z")

        assertEquals(Instant.parse("2026-09-28T08:00:00Z"), InformationRequestClockCalculator.advance(calendar, fridayAfternoon, 2 * HOUR))
        assertEquals(
            Instant.parse("2026-09-28T08:00:00Z"),
            InformationRequestClockCalculator.advance(calendar, Instant.parse("2026-09-26T10:00:00Z"), HOUR),
        )
    }

    @Test
    fun `a holiday of the frozen calendar is not working time`()
    {
        val calendar = weekdays(johannesburg).copy(holidays = setOf(LocalDate.parse("2026-09-28")))

        assertEquals(
            Instant.parse("2026-09-29T08:00:00Z"),
            InformationRequestClockCalculator.advance(calendar, Instant.parse("2026-09-25T14:00:00Z"), 2 * HOUR),
        )
        assertEquals(
            HOUR,
            InformationRequestClockCalculator.elapsed(
                calendar,
                Instant.parse("2026-09-25T14:00:00Z"),
                Instant.parse("2026-09-29T07:00:00Z"),
            ),
        )
    }

    @Test
    fun `working hours follow the local wall clock across a daylight saving change`()
    {
        val calendar = InformationRequestClockCalendar(
            InformationRequestClockType.BUSINESS,
            berlin,
            DayOfWeek.entries.map { InformationRequestWorkingPeriod(it, 9 * 60, 17 * 60) },
            emptySet(),
        )

        assertEquals(
            Instant.parse("2026-03-29T07:30:00Z"),
            InformationRequestClockCalculator.advance(calendar, Instant.parse("2026-03-28T15:30:00Z"), HOUR),
        )
        assertEquals(
            Instant.parse("2026-03-29T08:00:00Z"),
            InformationRequestClockCalculator.advance(calendar, Instant.parse("2026-03-29T06:00:00Z"), HOUR),
        )
    }

    @Test
    fun `elapsed working time and a backward walk are the inverse of advancing`()
    {
        val calendar = weekdays(johannesburg)
        val received = Instant.parse("2026-09-25T14:00:00Z")
        val due = InformationRequestClockCalculator.advance(calendar, received, 16 * HOUR)

        assertEquals(Instant.parse("2026-09-29T14:00:00Z"), due)
        assertEquals(16 * HOUR, InformationRequestClockCalculator.elapsed(calendar, received, due))
        assertEquals(30 * MINUTE, InformationRequestClockCalculator.elapsed(calendar, received, received.plusSeconds(30 * MINUTE)))
        assertEquals(Instant.parse("2026-09-29T13:00:00Z"), InformationRequestClockCalculator.retreat(calendar, due, HOUR))
        assertEquals(Instant.parse("2026-09-25T14:00:00Z"), InformationRequestClockCalculator.retreat(calendar, due, 16 * HOUR))
        assertEquals(0, InformationRequestClockCalculator.elapsed(calendar, due, received))
    }

    @Test
    fun `working periods a spring gap resolves onto the same time are counted once by every walk`() {
        val calendar = sundays(berlin, 2 * 60 to 2 * 60 + 30, 3 * 60 to 4 * 60)
        val received = Instant.parse("2026-03-29T01:00:00Z")
        val due = InformationRequestClockCalculator.advance(calendar, received, HOUR)

        assertEquals(Instant.parse("2026-03-29T02:00:00Z"), due)
        assertEquals(HOUR, InformationRequestClockCalculator.elapsed(calendar, received, due))
        assertEquals(received, InformationRequestClockCalculator.retreat(calendar, due, HOUR))
        assertEquals(
            due,
            InformationRequestClockCalculator.advance(
                calendar,
                received,
                InformationRequestClockCalculator.elapsed(calendar, received, due)
            )
        )
        assertEquals(
            30 * MINUTE,
            InformationRequestClockCalculator.elapsed(calendar, received.plusSeconds(30 * MINUTE), due)
        )
    }

    @Test
    fun `working periods a spring gap moves out of order are walked in time order`() {
        val calendar = sundays(berlin, 2 * 60 + 30 to 2 * 60 + 45, 3 * 60 to 3 * 60 + 20)
        val received = Instant.parse("2026-03-29T01:00:00Z")
        val due = InformationRequestClockCalculator.advance(calendar, received, 35 * MINUTE)

        assertEquals(Instant.parse("2026-03-29T01:45:00Z"), due)
        assertEquals(35 * MINUTE, InformationRequestClockCalculator.elapsed(calendar, received, due))
        assertEquals(received, InformationRequestClockCalculator.retreat(calendar, due, 35 * MINUTE))
        assertEquals(
            Instant.parse("2026-03-29T01:20:00Z"),
            InformationRequestClockCalculator.advance(calendar, received, 20 * MINUTE)
        )
    }

    @Test
    fun `split working periods across an autumn repeated hour count the real time that passes`() {
        val calendar = sundays(berlin, 60 to 2 * 60 + 30, 2 * 60 + 30 to 4 * 60)
        val received = Instant.parse("2026-10-24T23:00:00Z")
        val due = InformationRequestClockCalculator.advance(calendar, received, 4 * HOUR)

        assertEquals(Instant.parse("2026-10-25T03:00:00Z"), due)
        assertEquals(4 * HOUR, InformationRequestClockCalculator.elapsed(calendar, received, due))
        assertEquals(received, InformationRequestClockCalculator.retreat(calendar, due, 4 * HOUR))
        val earlier = InformationRequestClockCalculator.advance(calendar, received, 3 * HOUR)
        assertEquals(Instant.parse("2026-10-25T02:00:00Z"), earlier)
        assertEquals(3 * HOUR, InformationRequestClockCalculator.elapsed(calendar, received, earlier))
    }

    @Test
    fun `split working periods on ordinary dates skip the break and holidays in every walk`() {
        val calendar = InformationRequestClockCalendar(
            InformationRequestClockType.BUSINESS,
            johannesburg,
            WEEKDAYS.flatMap {
                listOf(
                    InformationRequestWorkingPeriod(it, 9 * 60, 12 * 60),
                    InformationRequestWorkingPeriod(it, 13 * 60, 17 * 60)
                )
            },
            setOf(LocalDate.parse("2026-09-28")),
        )
        val received = Instant.parse("2026-09-25T14:00:00Z")
        val due = InformationRequestClockCalculator.advance(calendar, received, 3 * HOUR)

        assertEquals(Instant.parse("2026-09-29T09:00:00Z"), due)
        assertEquals(3 * HOUR, InformationRequestClockCalculator.elapsed(calendar, received, due))
        assertEquals(received, InformationRequestClockCalculator.retreat(calendar, due, 3 * HOUR))
        assertEquals(
            HOUR,
            InformationRequestClockCalculator.elapsed(
                calendar,
                Instant.parse("2026-09-29T09:30:00Z"),
                Instant.parse("2026-09-29T11:30:00Z"),
            ),
        )
    }

    @Test
    fun `a working period ending at midnight lasts the whole local day including a shortened one`() {
        val calendar = sundays(berlin, 0 to 24 * 60)
        val dayStart = Instant.parse("2026-03-28T23:00:00Z")
        val dayEnd = Instant.parse("2026-03-29T22:00:00Z")

        assertEquals(23 * HOUR, InformationRequestClockCalculator.elapsed(calendar, dayStart, dayEnd))
        assertEquals(dayEnd, InformationRequestClockCalculator.advance(calendar, dayStart, 23 * HOUR))
        assertEquals(dayStart, InformationRequestClockCalculator.retreat(calendar, dayEnd, 23 * HOUR))
        assertEquals(
            Instant.parse("2026-04-04T23:00:00Z"),
            InformationRequestClockCalculator.advance(calendar, dayStart, 24 * HOUR),
        )
    }

    @Test
    fun `a business calendar without working time cannot produce a due time`()
    {
        val calendar = InformationRequestClockCalendar(InformationRequestClockType.BUSINESS, johannesburg, emptyList(), emptySet())

        assertThrows<IllegalArgumentException> {
            InformationRequestClockCalculator.advance(calendar, Instant.parse("2026-09-25T14:00:00Z"), HOUR)
        }
    }

    private fun weekdays(zone: ZoneId) = InformationRequestClockCalendar(
        InformationRequestClockType.BUSINESS,
        zone,
        WEEKDAYS.map { InformationRequestWorkingPeriod(it, 9 * 60, 17 * 60) },
        emptySet(),
    )

    private fun sundays(zone: ZoneId, vararg periods: Pair<Int, Int>) = InformationRequestClockCalendar(
        InformationRequestClockType.BUSINESS,
        zone,
        periods.map { (start, end) -> InformationRequestWorkingPeriod(DayOfWeek.SUNDAY, start, end) },
        emptySet(),
    )

    private companion object
    {
        const val MINUTE = 60L
        const val HOUR = 3600L
        const val DAY = 86400L
        val WEEKDAYS =
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}
