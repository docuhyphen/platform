package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.JsonElement
import java.util.*

data class FieldValueRevisionValue(
    val revisionId: UUID,
    val fieldContractId: UUID,
    val valueType: FieldValueType,
    val cleared: Boolean,
    val value: JsonElement,
)
