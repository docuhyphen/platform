package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationKind
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class InformationRequestItemProblemDto(
    val code: String,
    val message: String,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID? = null,
    val occurrencePath: String? = null,
    @Serializable(with = UUIDSerializer::class) val fieldContractId: UUID? = null,
    val validationKind: InformationRequestStructuredResponseValidationKind? = null,
)

@Serializable
data class InformationRequestItemRefusalDto(
    val errorMessage: String,
    val reasonCode: String,
    val problems: List<InformationRequestItemProblemDto> = emptyList(),
    val undisclosedProblemCount: Int = 0,
)
