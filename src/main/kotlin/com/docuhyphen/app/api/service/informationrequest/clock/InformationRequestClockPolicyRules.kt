package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.informationrequest.clock.*
import java.time.DateTimeException
import java.time.ZoneId

object InformationRequestClockPolicyRules {
    fun termsOf(definition: InformationRequestClockPolicyDefinition) = InformationRequestClockPolicyTerms(
        clockType = definition.clockType,
        businessTimezone = definition.businessTimezone,
        standardDurationMinutes = definition.standardDurationMinutes,
        urgentDurationMinutes = definition.urgentDurationMinutes,
        escalationAfterMinutes = definition.escalationAfterMinutes,
        workingPeriods = definition.workingPeriods.map {
            InformationRequestClockPolicyPeriodTerms(it.dayOfWeek.value, it.startMinute, it.endMinute)
        },
        reminderMinutesBeforeDue = definition.reminderMinutesBeforeDue,
    )

    fun problemsOf(terms: InformationRequestClockPolicyTerms): List<InformationRequestClockPolicyProblem> {
        val problems = mutableListOf<InformationRequestClockPolicyProblem>()
        fun add(term: InformationRequestClockPolicyTerm, index: Int?, code: String, message: String) {
            problems += InformationRequestClockPolicyProblem(term, index, code, message)
        }

        val maximumDuration = if (terms.clockType == InformationRequestClockType.BUSINESS)
            terms.workingPeriods.filter(::withinOneDay).sumOf { it.endMinute - it.startMinute }.toLong()
                .takeIf { it > 0 }?.times(500)
                ?.coerceAtMost(InformationRequestClockInputLimits.MAXIMUM_DURATION_MINUTES.toLong())
                ?: InformationRequestClockInputLimits.MAXIMUM_DURATION_MINUTES.toLong()
        else InformationRequestClockInputLimits.MAXIMUM_DURATION_MINUTES.toLong()
        if (terms.standardDurationMinutes > maximumDuration)
            add(
                InformationRequestClockPolicyTerm.STANDARD_DURATION,
                null,
                "duration.too_large",
                "The standard duration fits within 500 working weeks or ten calendar years"
            )
        if (terms.urgentDurationMinutes > maximumDuration)
            add(
                InformationRequestClockPolicyTerm.URGENT_DURATION,
                null,
                "duration.too_large",
                "The urgent duration fits within the supported clock horizon"
            )
        if (terms.workingPeriods.size > 168)
            add(
                InformationRequestClockPolicyTerm.WORKING_PERIODS,
                null,
                "collection.too_large",
                "A clock policy states at most 168 working periods"
            )
        if (terms.reminderMinutesBeforeDue.size > 128)
            add(
                InformationRequestClockPolicyTerm.REMINDERS,
                null,
                "collection.too_large",
                "A clock policy states at most 128 reminders"
            )
        if (terms.reminderMinutesBeforeDue.any { it > InformationRequestClockInputLimits.MAXIMUM_DURATION_MINUTES })
            add(
                InformationRequestClockPolicyTerm.REMINDERS,
                null,
                "duration.too_large",
                "A reminder offset fits within ten years"
            )
        if ((terms.escalationAfterMinutes ?: 0) > InformationRequestClockInputLimits.MAXIMUM_DURATION_MINUTES)
            add(
                InformationRequestClockPolicyTerm.ESCALATION,
                null,
                "duration.too_large",
                "An escalation delay fits within ten years"
            )
        if (!knownZone(terms.businessTimezone.trim())) {
            add(
                InformationRequestClockPolicyTerm.BUSINESS_TIMEZONE,
                null,
                "timezone.unknown",
                "A clock policy names a valid business timezone"
            )
        }
        if (terms.standardDurationMinutes <= 0) {
            add(
                InformationRequestClockPolicyTerm.STANDARD_DURATION,
                null,
                "duration.invalid",
                "A clock policy's standard duration is positive"
            )
        }
        if (terms.urgentDurationMinutes <= 0 || terms.urgentDurationMinutes > terms.standardDurationMinutes) {
            add(
                InformationRequestClockPolicyTerm.URGENT_DURATION,
                null,
                "duration.invalid",
                "A clock policy's urgent duration is positive and no longer than its standard duration",
            )
        }
        if (terms.clockType == InformationRequestClockType.BUSINESS && terms.workingPeriods.isEmpty()) {
            add(
                InformationRequestClockPolicyTerm.WORKING_PERIODS,
                null,
                "collection.empty",
                "A business clock policy states at least one working period",
            )
        }
        if (terms.clockType == InformationRequestClockType.CALENDAR && terms.workingPeriods.isNotEmpty()) {
            add(
                InformationRequestClockPolicyTerm.WORKING_PERIODS,
                null,
                "period.not_applicable",
                "A calendar clock policy counts every minute, so it states no working periods",
            )
        }
        terms.workingPeriods.forEachIndexed { index, period ->
            if (!withinOneDay(period)) {
                add(
                    InformationRequestClockPolicyTerm.WORKING_PERIODS,
                    index,
                    "period.invalid",
                    "A working period starts before it ends within one day",
                )
            }
        }
        overlappingPeriods(terms.workingPeriods).forEach { index ->
            add(
                InformationRequestClockPolicyTerm.WORKING_PERIODS,
                index,
                "period.overlap",
                "Working periods of one day do not overlap"
            )
        }
        terms.reminderMinutesBeforeDue.forEachIndexed { index, minutes ->
            if (minutes <= 0) {
                add(
                    InformationRequestClockPolicyTerm.REMINDERS,
                    index,
                    "duration.invalid",
                    "A reminder point is a positive number of minutes before the due time",
                )
            } else if (terms.reminderMinutesBeforeDue.indexOf(minutes) < index) {
                add(
                    InformationRequestClockPolicyTerm.REMINDERS,
                    index,
                    "collection.duplicate",
                    "Reminder points are distinct"
                )
            }
        }
        if (terms.escalationAfterMinutes?.let { it < 0 } == true) {
            add(
                InformationRequestClockPolicyTerm.ESCALATION,
                null,
                "duration.invalid",
                "An escalation point is zero or more minutes after the due time",
            )
        }
        return problems
    }

    private fun withinOneDay(period: InformationRequestClockPolicyPeriodTerms): Boolean =
        period.dayOfWeek in 1..7 &&
                period.startMinute >= 0 &&
                period.endMinute <= MINUTES_PER_DAY &&
                period.startMinute < period.endMinute

    private fun overlappingPeriods(periods: List<InformationRequestClockPolicyPeriodTerms>): List<Int> =
        periods.withIndex()
            .filter { withinOneDay(it.value) }
            .groupBy { it.value.dayOfWeek }
            .values
            .flatMap { day ->
                var reachedMinute = -1
                day.sortedWith(compareBy({ it.value.startMinute }, { it.index })).mapNotNull { period ->
                    val overlaps = period.value.startMinute < reachedMinute
                    reachedMinute = maxOf(reachedMinute, period.value.endMinute)
                    period.index.takeIf { overlaps }
                }
            }
            .sorted()

    private fun knownZone(zone: String): Boolean =
        try {
            ZoneId.of(zone)
            true
        } catch (_: DateTimeException) {
            false
        }

    private const val MINUTES_PER_DAY = 1440
}
