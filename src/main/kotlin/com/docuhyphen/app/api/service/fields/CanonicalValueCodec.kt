package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Timestamp

object CanonicalValueCodec
{
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

            FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT -> Unit
        }
    }

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

    fun toJson(canonical: CanonicalFieldValue, numberScale: Int? = null): JsonElement =
        toJson(FieldValue().also { applyTo(it, canonical) }, canonical.selectionCodes, numberScale)

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
