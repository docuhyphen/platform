package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class HiddenResponseClearTargetDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceTemplateBindingId: UUID,
    val occurrencePath: String,
)

@Serializable
data class HiddenResponseClearRequiredErrorDto(
    val errorMessage: String,
    val reasonCode: String,
    val hiddenResponseClears: List<HiddenResponseClearTargetDto>,
)
