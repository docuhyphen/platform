package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import java.time.Instant
import java.time.ZoneOffset

object InformationRequestRecurrenceSchedule
{
    fun dueAt(recurrence: InformationRequestRecurrence, sequence: Int): Instant
    {
        val steps = (sequence - 1).toLong() * recurrence.intervalCount
        val first = recurrence.firstDueAt.toInstant().atZone(ZoneOffset.UTC)
        return when (recurrence.intervalUnit)
        {
            InformationRequestRecurrenceUnit.DAY -> first.plusDays(steps)
            InformationRequestRecurrenceUnit.WEEK -> first.plusWeeks(steps)
            InformationRequestRecurrenceUnit.MONTH -> first.plusMonths(steps)
            InformationRequestRecurrenceUnit.YEAR -> first.plusYears(steps)
        }.toInstant()
    }

    fun nextDueAt(recurrence: InformationRequestRecurrence, occurrencesCreated: Int): Instant?
    {
        val next = occurrencesCreated + 1
        val maximum = recurrence.maximumOccurrences
        return if (maximum != null && next > maximum) null else dueAt(recurrence, next)
    }
}
