package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.HiddenResponseClearRequiredErrorDto
import com.docuhyphen.app.api.model.dto.HiddenResponseClearTargetDto
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException

object InformationRequestHiddenResponseClearDtoMapper {
    fun toErrorDto(exception: InformationRequestHiddenResponseClearRequiredException): HiddenResponseClearRequiredErrorDto =
        HiddenResponseClearRequiredErrorDto(
            errorMessage = exception.message,
            reasonCode = exception.reasonCode,
            hiddenResponseClears = exception.targets.map {
                HiddenResponseClearTargetDto(it.requirementId, it.sourceTemplateBindingId, it.occurrencePath)
            },
        )
}
