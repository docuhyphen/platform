package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object InformationRequestFollowUpScheduleBounds {
    const val MAXIMUM_INTERVAL_COUNT = 1000
    private val EARLIEST_FIRST_DUE: Instant = Instant.EPOCH
    private val FIRST_DUE_LIMIT: Instant = LocalDate.of(10000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()

    fun requireValid(command: DefineInformationRequestRecurrenceCommand) {
        if (command.intervalCount !in 1..MAXIMUM_INTERVAL_COUNT) {
            refuse("A recurrence repeats every 1 to $MAXIMUM_INTERVAL_COUNT days, weeks, months, or years")
        }
        if (command.maximumOccurrences?.let { it < 1 } == true) {
            refuse("A recurrence that stops does so after at least one request")
        }
        if (command.firstDueAt.isBefore(EARLIEST_FIRST_DUE) || !command.firstDueAt.isBefore(FIRST_DUE_LIMIT)) {
            refuse("A recurrence is first due between the start of 1970 and the end of 9999")
        }
    }

    fun requireValid(command: DefineInformationRequestRefreshRuleCommand) {
        if (command.leadDays < 0) {
            refuse("A refresh rule asks again zero or more days before expiry")
        }
    }

    private fun refuse(message: String): Nothing =
        throw InformationRequestCommandRequestException(
            message,
            InformationRequestErrorCatalog.FOLLOW_UP_SCHEDULE_INVALID
        )
}
