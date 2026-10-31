package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class CanonicalFieldValue(
    val type: FieldValueType,
    val isEmpty: Boolean,
    val textValue: String? = null,
    val numberValue: BigDecimal? = null,
    val boolValue: Boolean? = null,
    val dateValue: LocalDate? = null,
    val datetimeValue: Instant? = null,
    val datetimeOffsetMinutes: Int? = null,
    val selectionCodes: List<String> = emptyList(),
)
{
    companion object
    {
        fun empty(type: FieldValueType) = CanonicalFieldValue(type = type, isEmpty = true)
    }
}
