package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestExecutionStandingDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestExecutionStanding

object InformationRequestExecutionStandingDtoMapper
{
    fun toDto(standing: InformationRequestExecutionStanding): InformationRequestExecutionStandingDto =
        InformationRequestExecutionStandingDto(kind = standing.kind, reason = standing.reason)
}
