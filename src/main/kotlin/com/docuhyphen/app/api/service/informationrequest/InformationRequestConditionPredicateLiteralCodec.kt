package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicate
import com.docuhyphen.app.api.service.fields.CanonicalDateTime
import com.docuhyphen.app.api.service.fields.CanonicalNumber
import com.docuhyphen.app.api.service.fields.FieldOperator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate

/**
 * Converts a condition predicate's authored literal between the canonical JSON it is authored and
 * read as and the typed scalar columns it is stored as, the same convention
 * [com.docuhyphen.app.api.service.fields.CanonicalValueCodec] uses for a Field's own stored answer.
 * An `IN` / `NOT_IN` predicate's literal is a list rather than a scalar, so it is handled
 * separately as the ordered rows of [com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicateLiteral].
 */
object InformationRequestConditionPredicateLiteralCodec
{
    private val LIST_OPERATORS = setOf(FieldOperator.IN, FieldOperator.NOT_IN)

    /** Writes the scalar literal onto [target]'s typed columns. Callers write list literals separately. */
    fun applyScalarTo(target: InformationRequestTemplateConditionPredicate, value: JsonElement?)
    {
        target.textValue = null
        target.numberValue = null
        target.boolValue = null
        target.dateValue = null
        target.datetimeValue = null
        target.datetimeOffsetMinutes = null

        val type = target.valueType
        if (type == null || target.operator in LIST_OPERATORS || value == null) return
        when (type)
        {
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT,
            FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT ->
                target.textValue = literalString(value)
            FieldValueType.BOOLEAN -> target.boolValue = literalBoolean(value)
            FieldValueType.INTEGER, FieldValueType.DECIMAL -> target.numberValue = literalNumber(value)
            FieldValueType.DATE ->
                target.dateValue = literalString(value)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            FieldValueType.DATE_TIME -> literalString(value)?.let(CanonicalDateTime::parse)?.let { reading ->
                target.datetimeValue = Timestamp.from(reading.instant)
                target.datetimeOffsetMinutes = reading.offsetMinutes
            }
        }
    }

    /** The list literal rows an `IN` / `NOT_IN` predicate stores; empty for every other predicate. */
    fun listLiteralValues(operator: FieldOperator, value: JsonElement?): List<String> =
        if (operator !in LIST_OPERATORS) emptyList()
        else (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    /** Reconstructs the canonical literal a stored predicate compares against. */
    fun toJson(predicate: InformationRequestTemplateConditionPredicate, literalValues: List<String>): JsonElement?
    {
        if (predicate.operator in LIST_OPERATORS)
        {
            return literalValues.takeIf { it.isNotEmpty() }?.let { values -> JsonArray(values.map { JsonPrimitive(it) }) }
        }
        return when (predicate.valueType)
        {
            null -> null
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT,
            FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT ->
                predicate.textValue?.let { JsonPrimitive(it) }
            FieldValueType.BOOLEAN -> predicate.boolValue?.let { JsonPrimitive(it) }
            FieldValueType.INTEGER, FieldValueType.DECIMAL ->
                predicate.numberValue?.let { JsonPrimitive(CanonicalNumber.text(predicate.valueType!!, it, null)) }
            FieldValueType.DATE -> predicate.dateValue?.let { JsonPrimitive(it.toString()) }
            FieldValueType.DATE_TIME -> predicate.datetimeValue?.let {
                JsonPrimitive(CanonicalDateTime.format(it.toInstant(), predicate.datetimeOffsetMinutes))
            }
        }
    }

    private fun literalString(value: JsonElement): String? = (value as? JsonPrimitive)?.content

    private fun literalBoolean(value: JsonElement): Boolean? = (value as? JsonPrimitive)?.booleanOrNull

    private fun literalNumber(value: JsonElement): BigDecimal? =
        (value as? JsonPrimitive)?.content?.trim()?.let { runCatching { BigDecimal(it) }.getOrNull() }
}
