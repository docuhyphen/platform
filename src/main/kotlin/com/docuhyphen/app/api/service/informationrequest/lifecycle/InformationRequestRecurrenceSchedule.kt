package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.InformationRequestLineage
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

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

    fun followedRequestId(
        recurrence: InformationRequestRecurrence,
        series: List<InformationRequestLineage>,
        stateOf: (UUID) -> InformationRequestState?,
    ): UUID =
        series.asReversed()
            .map { it.successorRequestId }
            .firstOrNull { occurrenceId -> stateOf(occurrenceId)?.let(InformationRequestTransitionMatrix::followsFrom) == true }
            ?: recurrence.originRequestId

    fun nextDueAt(recurrence: InformationRequestRecurrence, occurrencesCreated: Int): Instant?
    {
        val next = occurrencesCreated + 1
        val maximum = recurrence.maximumOccurrences
        return if (maximum != null && next > maximum) null else dueAt(recurrence, next)
    }
}
