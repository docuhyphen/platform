package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyPeriodTerms
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyTerm
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyTerms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InformationRequestClockPolicyRulesTest {
    @Test
    fun `every period that starts before an earlier period of its day ends is named`() {
        val problems = InformationRequestClockPolicyRules.problemsOf(
            terms(
                periods = listOf(
                    period(1, 540, 1020),
                    period(1, 600, 700),
                    period(1, 800, 900),
                    period(1, 1020, 1080),
                    period(2, 600, 700),
                    period(2, 9, 8),
                ),
            ),
        )

        assertEquals(
            listOf("period.invalid@5", "period.overlap@1", "period.overlap@2"),
            problems.map { "${it.code}@${it.index}" },
        )
        assertEquals(setOf(InformationRequestClockPolicyTerm.WORKING_PERIODS), problems.map { it.term }.toSet())
    }

    @Test
    fun `an explicit zero escalation delay is a valid immediate escalation and an absent one is no escalation`() {
        assertEquals(emptyList<Any>(), InformationRequestClockPolicyRules.problemsOf(terms(escalation = 0)))
        assertEquals(emptyList<Any>(), InformationRequestClockPolicyRules.problemsOf(terms(escalation = null)))
        assertEquals(
            listOf(InformationRequestClockPolicyTerm.ESCALATION),
            InformationRequestClockPolicyRules.problemsOf(terms(escalation = -1)).map { it.term },
        )
    }

    @Test
    fun `a repeated reminder is named at each later position and a non-positive one is named once`() {
        val problems = InformationRequestClockPolicyRules.problemsOf(terms(reminders = listOf(60, 0, 60, 0, 60)))

        assertEquals(
            listOf("duration.invalid@1", "collection.duplicate@2", "duration.invalid@3", "collection.duplicate@4"),
            problems.map { "${it.code}@${it.index}" },
        )
    }

    @Test
    fun `unbounded durations and collections are rejected before publication`() {
        val duration =
            InformationRequestClockPolicyRules.problemsOf(terms().copy(standardDurationMinutes = Int.MAX_VALUE))
        org.junit.jupiter.api.Assertions.assertTrue(duration.any { it.term == InformationRequestClockPolicyTerm.STANDARD_DURATION })
        val reminders = InformationRequestClockPolicyRules.problemsOf(terms(reminders = (1..129).toList()))
        org.junit.jupiter.api.Assertions.assertTrue(reminders.any { it.term == InformationRequestClockPolicyTerm.REMINDERS && it.code == "collection.too_large" })
        val periods = InformationRequestClockPolicyRules.problemsOf(terms(periods = (1..169).map { period(1, 0, 1) }))
        org.junit.jupiter.api.Assertions.assertTrue(periods.any { it.term == InformationRequestClockPolicyTerm.WORKING_PERIODS && it.code == "collection.too_large" })
    }

    private fun terms(
        periods: List<InformationRequestClockPolicyPeriodTerms> = listOf(period(1, 540, 1020)),
        reminders: List<Int> = listOf(60),
        escalation: Int? = 120,
    ) = InformationRequestClockPolicyTerms(
        clockType = InformationRequestClockType.BUSINESS,
        businessTimezone = "Europe/Berlin",
        standardDurationMinutes = 2400,
        urgentDurationMinutes = 480,
        escalationAfterMinutes = escalation,
        workingPeriods = periods,
        reminderMinutesBeforeDue = reminders,
    )

    private fun period(day: Int, start: Int, end: Int) = InformationRequestClockPolicyPeriodTerms(day, start, end)
}
