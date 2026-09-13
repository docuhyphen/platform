package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * A validated, canonical field value ready to persist onto a
 * [com.docuhyphen.app.api.model.entity.FieldValue]. Exactly one representation is populated for a
 * non-empty value; [isEmpty] denotes a cleared or absent value. Consumers must not parse display
 * strings; they read the typed representation here.
 */
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
