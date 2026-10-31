package com.docuhyphen.app.api.model.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClockType

data class InformationRequestClockPolicyTerms(
    val clockType: InformationRequestClockType,
    val businessTimezone: String,
    val standardDurationMinutes: Int,
    val urgentDurationMinutes: Int,
    val escalationAfterMinutes: Int?,
    val workingPeriods: List<InformationRequestClockPolicyPeriodTerms>,
    val reminderMinutesBeforeDue: List<Int>,
)

data class InformationRequestClockPolicyPeriodTerms(
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
)

enum class InformationRequestClockPolicyTerm {
    BUSINESS_TIMEZONE,
    STANDARD_DURATION,
    URGENT_DURATION,
    WORKING_PERIODS,
    REMINDERS,
    ESCALATION,
}

data class InformationRequestClockPolicyProblem(
    val term: InformationRequestClockPolicyTerm,
    val index: Int?,
    val code: String,
    val message: String,
)
