package com.docuhyphen.app.api.model.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class InformationRequestWorkingPeriod(
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
)

data class InformationRequestClockCalendar(
    val clockType: InformationRequestClockType,
    val zone: ZoneId,
    val periods: List<InformationRequestWorkingPeriod>,
    val holidays: Set<LocalDate>,
)
