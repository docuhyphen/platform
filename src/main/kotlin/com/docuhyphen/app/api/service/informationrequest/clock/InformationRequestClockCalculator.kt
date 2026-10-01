package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockCalendar
import java.time.*

object InformationRequestClockCalculator
{
    private const val MAXIMUM_DAYS = 3660L
    private const val MINUTES_PER_DAY = 1440

    fun advance(calendar: InformationRequestClockCalendar, from: Instant, seconds: Long): Instant
    {
        require(seconds >= 0) { "A clock advances by a non-negative duration" }
        if (calendar.clockType == InformationRequestClockType.CALENDAR) return from.plusSeconds(seconds)
        requireWorkingTime(calendar)
        if (seconds == 0L) return from
        var remaining = seconds
        var cursor = from
        var date = localDate(calendar, from)
        repeat(MAXIMUM_DAYS.toInt()) {
            for (window in windowsOf(calendar, date))
            {
                val start = maxOf(cursor, window.first)
                if (start >= window.second) continue
                val available = Duration.between(start, window.second).seconds
                if (remaining <= available) return start.plusSeconds(remaining)
                remaining -= available
                cursor = window.second
            }
            date = date.plusDays(1)
        }
        throw IllegalStateException("The clock calendar has no working time within ten years")
    }

    fun elapsed(calendar: InformationRequestClockCalendar, from: Instant, to: Instant): Long
    {
        if (!to.isAfter(from)) return 0
        if (calendar.clockType == InformationRequestClockType.CALENDAR) return Duration.between(from, to).seconds
        requireWorkingTime(calendar)
        var total = 0L
        var date = localDate(calendar, from).minusDays(1)
        val last = localDate(calendar, to).plusDays(1)
        while (!date.isAfter(last))
        {
            for (window in windowsOf(calendar, date))
            {
                val start = maxOf(from, window.first)
                val end = minOf(to, window.second)
                if (end.isAfter(start)) total += Duration.between(start, end).seconds
            }
            date = date.plusDays(1)
        }
        return total
    }

    fun retreat(calendar: InformationRequestClockCalendar, from: Instant, seconds: Long): Instant
    {
        require(seconds >= 0) { "A clock retreats by a non-negative duration" }
        if (calendar.clockType == InformationRequestClockType.CALENDAR) return from.minusSeconds(seconds)
        requireWorkingTime(calendar)
        if (seconds == 0L) return from
        var remaining = seconds
        var cursor = from
        var date = localDate(calendar, from)
        repeat(MAXIMUM_DAYS.toInt()) {
            for (window in windowsOf(calendar, date).asReversed())
            {
                val end = minOf(cursor, window.second)
                if (end <= window.first) continue
                val available = Duration.between(window.first, end).seconds
                if (remaining <= available) return end.minusSeconds(remaining)
                remaining -= available
                cursor = window.first
            }
            date = date.minusDays(1)
        }
        throw IllegalStateException("The clock calendar has no working time within ten years")
    }

    private fun requireWorkingTime(calendar: InformationRequestClockCalendar)
    {
        require(calendar.periods.isNotEmpty()) { "A business clock calendar states at least one working period" }
    }

    private fun windowsOf(calendar: InformationRequestClockCalendar, date: LocalDate): List<Pair<Instant, Instant>>
    {
        if (date in calendar.holidays) return emptyList()
        return calendar.periods
            .filter { it.dayOfWeek == date.dayOfWeek }
            .sortedBy { it.startMinute }
            .map { localInstant(calendar, date, it.startMinute) to localInstant(calendar, date, it.endMinute) }
            .filter { it.second.isAfter(it.first) }
    }

    private fun localInstant(calendar: InformationRequestClockCalendar, date: LocalDate, minute: Int): Instant =
        if (minute >= MINUTES_PER_DAY)
            date.plusDays(1).atStartOfDay(calendar.zone).toInstant()
        else
            LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60)).atZone(calendar.zone).toInstant()

    private fun localDate(calendar: InformationRequestClockCalendar, instant: Instant): LocalDate =
        instant.atZone(calendar.zone).toLocalDate()
}
