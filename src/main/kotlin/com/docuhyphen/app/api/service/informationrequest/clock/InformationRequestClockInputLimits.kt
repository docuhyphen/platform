package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog

object InformationRequestClockInputLimits {
    const val MAXIMUM_DURATION_MINUTES = 5_256_000

    fun requireLength(value: String, maximum: Int, field: String) {
        if (value.length > maximum) refuse("$field holds at most $maximum characters")
    }

    fun refuse(message: String): Nothing = throw InformationRequestCommandRequestException(
        message, InformationRequestErrorCatalog.CLOCK_INPUT_INVALID,
    )
}