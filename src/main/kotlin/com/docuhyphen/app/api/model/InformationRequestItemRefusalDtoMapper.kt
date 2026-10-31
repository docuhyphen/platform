package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestItemProblemDto
import com.docuhyphen.app.api.model.dto.InformationRequestItemRefusalDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException

object InformationRequestItemRefusalDtoMapper {
    fun toDto(exception: InformationRequestItemRefusalException): InformationRequestItemRefusalDto =
        InformationRequestItemRefusalDto(
            errorMessage = exception.message,
            reasonCode = exception.reasonCode,
            problems = exception.problems.map(::toDto),
            undisclosedProblemCount = exception.undisclosedProblemCount,
        )

    fun toDto(problem: InformationRequestItemProblem): InformationRequestItemProblemDto =
        InformationRequestItemProblemDto(
            code = problem.code,
            message = problem.message,
            requirementId = problem.requirementId,
            occurrencePath = problem.occurrencePath,
            fieldContractId = problem.fieldContractId,
            validationKind = problem.validationKind,
        )
}
