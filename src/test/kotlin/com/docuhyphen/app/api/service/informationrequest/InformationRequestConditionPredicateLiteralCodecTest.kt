package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicate
import com.docuhyphen.app.api.service.fields.FieldOperator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

class InformationRequestConditionPredicateLiteralCodecTest
{
    private fun predicate(valueType: FieldValueType?, operator: FieldOperator) =
        InformationRequestTemplateConditionPredicate().apply {
            conditionRuleId = java.util.UUID.randomUUID()
            templateVersionId = java.util.UUID.randomUUID()
            this.valueType = valueType
            this.operator = operator
        }

    @Test
    fun `a text literal round trips through the scalar column`()
    {
        val target = predicate(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS)
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, JsonPrimitive("north-branch"))

        assertEquals("north-branch", target.textValue)
        assertEquals(
            JsonPrimitive("north-branch"),
            InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()),
        )
    }

    @Test
    fun `a numeric literal round trips without floating point drift`()
    {
        val target = predicate(FieldValueType.DECIMAL, FieldOperator.GREATER_THAN)
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, JsonPrimitive("1250.500000"))

        assertEquals(BigDecimal("1250.500000"), target.numberValue)
        assertEquals(
            JsonPrimitive("1250.5"),
            InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()),
        )
    }

    @Test
    fun `a boolean literal round trips through the scalar column`()
    {
        val target = predicate(FieldValueType.BOOLEAN, FieldOperator.EQUALS)
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, JsonPrimitive(true))

        assertEquals(true, target.boolValue)
        assertEquals(
            JsonPrimitive(true),
            InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()),
        )
    }

    @Test
    fun `a date literal round trips through the scalar column`()
    {
        val target = predicate(FieldValueType.DATE, FieldOperator.LESS_THAN_OR_EQUAL)
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, JsonPrimitive("2026-09-01"))

        assertEquals(LocalDate.parse("2026-09-01"), target.dateValue)
        assertEquals(
            JsonPrimitive("2026-09-01"),
            InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()),
        )
    }

    @Test
    fun `a date-time literal keeps the moment and the offset it was authored at`()
    {
        val target = predicate(FieldValueType.DATE_TIME, FieldOperator.GREATER_THAN_OR_EQUAL)
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, JsonPrimitive("2026-09-01T10:00:00+02:00"))

        assertEquals(Timestamp.from(Instant.parse("2026-09-01T08:00:00Z")), target.datetimeValue)
        assertEquals(120, target.datetimeOffsetMinutes)
        assertEquals(
            JsonPrimitive("2026-09-01T10:00:00+02:00"),
            InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()),
        )
    }

    @Test
    fun `a membership literal is a list rather than a scalar column`()
    {
        val target = predicate(FieldValueType.SINGLE_SELECT, FieldOperator.IN)
        val literal = JsonArray(listOf(JsonPrimitive("draft"), JsonPrimitive("issued")))

        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, literal)
        assertNull(target.textValue, "a membership literal is not stored on the scalar column")

        val listValues = InformationRequestConditionPredicateLiteralCodec.listLiteralValues(FieldOperator.IN, literal)
        assertEquals(listOf("draft", "issued"), listValues)
        assertEquals(literal, InformationRequestConditionPredicateLiteralCodec.toJson(target, listValues))
    }

    @Test
    fun `a valueless predicate has no literal to reconstruct`()
    {
        val target = predicate(FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY)
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(target, null)

        assertNull(InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()))
    }

    @Test
    fun `a disposition predicate names no field value type and reconstructs no literal`()
    {
        val target = predicate(valueType = null, operator = FieldOperator.EQUALS)

        assertNull(InformationRequestConditionPredicateLiteralCodec.toJson(target, emptyList()))
    }
}
