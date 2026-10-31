package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog

object InformationRequestReasonCodes {
    const val MAXIMUM_LENGTH = 128

    private val MACHINE_CODE = Regex("^[a-z0-9][a-z0-9._-]{0,${MAXIMUM_LENGTH - 1}}$")

    fun requireMachineCode(value: String?): String? {
        val code = value?.trim()?.ifBlank { null } ?: return null
        if (!MACHINE_CODE.matches(code)) {
            throw InformationRequestCommandRequestException(
                "A reason code is up to $MAXIMUM_LENGTH lowercase letters, digits, dots, dashes, or underscores",
                InformationRequestErrorCatalog.REASON_CODE_INVALID,
            )
        }
        return code
    }
}
