package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestExchangeCompletionRefusalDto
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionException

object InformationRequestCompletionDtoMapper
{
    fun refusal(exception: InformationRequestExchangeCompletionException) =
        InformationRequestExchangeCompletionRefusalDto(
            errorMessage = exception.message,
            reasonCode = exception.reasonCode,
            informationRequestIds = exception.requestIds.map { it.toString() },
        )
}
