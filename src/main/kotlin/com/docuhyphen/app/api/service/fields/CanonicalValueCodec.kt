package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Timestamp

/**
 * Converts between the stored typed [FieldValue] columns and the canonical JSON representation used
 * at API boundaries, and applies a validated [CanonicalFieldValue] onto a [FieldValue] entity.
 * The JSON form is the only representation crossing the API; consumers never parse display strings.
 *
 * A date-time is stored as the exact moment it names, with the submitted offset kept in its own
 * column, so the same moment written at two offsets stores identically and still reads back the way
 * it was given.
 */
object CanonicalValueCodec
{
    /** Writes the canonical scalar/selection state onto [target]. Selection rows are handled separately. */
    fun applyTo(target: FieldValue, canonical: CanonicalFieldValue)
    {
        target.valueType = canonical.type
        target.textValue = null
        target.numberValue = null
        target.boolValue = null
        target.dateValue = null
        target.datetimeValue = null
        target.datetimeOffsetMinutes = null
        if (canonical.isEmpty) return
        when (canonical.type)
        {
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT -> target.textValue = canonical.textValue
            FieldValueType.BOOLEAN -> target.boolValue = canonical.boolValue
            FieldValueType.INTEGER, FieldValueType.DECIMAL -> target.numberValue = canonical.numberValue
            FieldValueType.DATE -> target.dateValue = canonical.dateValue
            FieldValueType.DATE_TIME ->
            {
                target.datetimeValue = canonical.datetimeValue?.let { Timestamp.from(it) }
                target.datetimeOffsetMinutes = canonical.datetimeOffsetMinutes
            }
            FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT -> Unit // codes stored as child rows
        }
    }

    /**
     * Builds the canonical JSON element for a stored value plus its selection codes.
     *
     * Numbers are rendered as digits rather than JSON numbers so their width survives a client that
     * parses JSON numbers as binary floating point, and [numberScale] is the scale the value's
     * contract configures so the column padding does not reach the response. A date-time is written
     * at the offset it was submitted with.
     */
    fun toJson(value: FieldValue, selectionCodes: List<String>, numberScale: Int? = null): JsonElement =
        when (value.valueType)
    {
        FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT ->
            value.textValue?.let { JsonPrimitive(it) } ?: JsonNull
        FieldValueType.BOOLEAN ->
            value.boolValue?.let { JsonPrimitive(it) } ?: JsonNull
        FieldValueType.INTEGER, FieldValueType.DECIMAL ->
            value.numberValue?.let { JsonPrimitive(CanonicalNumber.text(value.valueType, it, numberScale)) }
                ?: JsonNull
        FieldValueType.DATE ->
            value.dateValue?.let { JsonPrimitive(it.toString()) } ?: JsonNull
        FieldValueType.DATE_TIME ->
            value.datetimeValue?.let {
                JsonPrimitive(CanonicalDateTime.format(it.toInstant(), value.datetimeOffsetMinutes))
            } ?: JsonNull
        FieldValueType.SINGLE_SELECT ->
            selectionCodes.firstOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        FieldValueType.MULTI_SELECT ->
            JsonArray(selectionCodes.map { JsonPrimitive(it) })
    }

    fun toCanonical(value: FieldValue, selectionCodes: List<String>): CanonicalFieldValue = CanonicalFieldValue(
        type = value.valueType,
        isEmpty = isEmpty(value, selectionCodes),
        textValue = value.textValue,
        numberValue = value.numberValue,
        boolValue = value.boolValue,
        dateValue = value.dateValue,
        datetimeValue = value.datetimeValue?.toInstant(),
        datetimeOffsetMinutes = value.datetimeOffsetMinutes,
        selectionCodes = selectionCodes,
    )

    fun isEmpty(value: FieldValue, selectionCodes: List<String>): Boolean = when (value.valueType)
    {
        FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT -> value.textValue.isNullOrBlank()
        FieldValueType.BOOLEAN -> value.boolValue == null
        FieldValueType.INTEGER, FieldValueType.DECIMAL -> value.numberValue == null
        FieldValueType.DATE -> value.dateValue == null
        FieldValueType.DATE_TIME -> value.datetimeValue == null
        FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT -> selectionCodes.isEmpty()
    }
}
