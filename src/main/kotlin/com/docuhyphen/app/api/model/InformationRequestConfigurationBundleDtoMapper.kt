package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestConfigurationBundleProblemDto
import com.docuhyphen.app.api.model.dto.InformationRequestConfigurationBundleValidationDto
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundleProblem

object InformationRequestConfigurationBundleDtoMapper
{
    fun toDto(problems: List<InformationRequestConfigurationBundleProblem>): InformationRequestConfigurationBundleValidationDto =
        InformationRequestConfigurationBundleValidationDto(
            valid = problems.isEmpty(),
            problems = problems.map { InformationRequestConfigurationBundleProblemDto(it.path, it.code, it.message) },
        )
}
