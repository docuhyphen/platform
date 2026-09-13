package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CanonicalValueCodecTest
{
    private fun value() = FieldValue().apply {
        schemaAssignmentId = java.util.UUID.randomUUID()
        fieldContractId = java.util.UUID.randomUUID()
        resourceType = "EXCHANGE"
        resourceId = java.util.UUID.randomUUID()
    }

    @Test
    fun `applyTo populates only the matching scalar column`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue(FieldValueType.INTEGER, isEmpty = false, numberValue = BigDecimal("42")))
        assertEquals(BigDecimal("42"), v.numberValue)
        assertNull(v.textValue)
        assertNull(v.boolValue)
    }

    @Test
    fun `applyTo clears all columns for an empty value`()
    {
        val v = value()
        v.textValue = "stale"
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue.empty(FieldValueType.SHORT_TEXT))
        assertNull(v.textValue)
        assertTrue(CanonicalValueCodec.isEmpty(v, emptyList()))
    }

    @Test
    fun `toJson renders text as string`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue(FieldValueType.SHORT_TEXT, isEmpty = false, textValue = "hello"))
        assertEquals("hello", CanonicalValueCodec.toJson(v, emptyList()).jsonPrimitive.content)
    }

    @Test
    fun `toJson renders boolean`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue(FieldValueType.BOOLEAN, isEmpty = false, boolValue = true))
        assertTrue(CanonicalValueCodec.toJson(v, emptyList()).jsonPrimitive.boolean)
    }

    @Test
    fun `toJson renders date as ISO string`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue(FieldValueType.DATE, isEmpty = false, dateValue = LocalDate.of(2026, 7, 2)))
        assertEquals("2026-07-02", CanonicalValueCodec.toJson(v, emptyList()).jsonPrimitive.content)
    }

    @Test
    fun `toJson renders datetime as ISO string`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(
            v,
            CanonicalFieldValue(
                FieldValueType.DATE_TIME,
                isEmpty = false,
                datetimeValue = Instant.parse("2026-07-02T09:30:00Z"),
                datetimeOffsetMinutes = 0,
            ),
        )
        assertEquals("2026-07-02T09:30:00Z", CanonicalValueCodec.toJson(v, emptyList()).jsonPrimitive.content)
    }

    @Test
    fun `toJson renders single select as first code`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue(FieldValueType.SINGLE_SELECT, isEmpty = false, selectionCodes = listOf("a")))
        assertEquals("a", CanonicalValueCodec.toJson(v, listOf("a")).jsonPrimitive.content)
    }

    @Test
    fun `toJson renders multi select as array of codes`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue(FieldValueType.MULTI_SELECT, isEmpty = false, selectionCodes = listOf("a", "b")))
        val json = CanonicalValueCodec.toJson(v, listOf("a", "b"))
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))), json)
    }

    @Test
    fun `toJson renders empty scalar as JsonNull`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue.empty(FieldValueType.SHORT_TEXT))
        assertEquals(JsonNull, CanonicalValueCodec.toJson(v, emptyList()))
    }

    @Test
    fun `isEmpty is true for empty multi select`()
    {
        val v = value()
        CanonicalValueCodec.applyTo(v, CanonicalFieldValue.empty(FieldValueType.MULTI_SELECT))
        assertTrue(CanonicalValueCodec.isEmpty(v, emptyList()))
        assertFalse(CanonicalValueCodec.isEmpty(v, listOf("a")))
    }
}
