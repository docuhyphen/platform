package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRefusalDto
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException

object InformationRequestTemplateRefusalDtoMapper {
    fun toDto(exception: InformationRequestTemplateValidationException) = InformationRequestTemplateRefusalDto(
        errorMessage = exception.message,
        reasonCode = InformationRequestErrorCatalog.TEMPLATE_INVALID,
        sectionKey = exception.sectionKey,
        requirementKey = exception.requirementKey,
        groupKey = exception.groupKey,
        conditionRuleKey = exception.conditionRuleKey,
        reviewStageKey = exception.reviewStageKey,
    )
}
