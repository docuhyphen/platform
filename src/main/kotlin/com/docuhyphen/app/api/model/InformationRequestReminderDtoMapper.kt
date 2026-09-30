package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestReminderResultDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReminderResult

object InformationRequestReminderDtoMapper
{
    fun toDto(result: InformationRequestReminderResult): InformationRequestReminderResultDto =
        InformationRequestReminderResultDto(result.requestId, result.noticeCount, result.cooldownUntil?.toString())
}
