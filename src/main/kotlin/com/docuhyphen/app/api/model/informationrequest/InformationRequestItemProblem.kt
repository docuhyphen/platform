package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationKind
import java.util.*

data class InformationRequestItemProblem(
    val code: String,
    val message: String,
    val requirementId: UUID? = null,
    val occurrencePath: String? = null,
    val fieldContractId: UUID? = null,
    val validationKind: InformationRequestStructuredResponseValidationKind? = null,
)
