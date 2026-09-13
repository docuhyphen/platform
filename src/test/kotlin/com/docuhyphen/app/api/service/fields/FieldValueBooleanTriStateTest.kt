package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A yes or no field has three readings, not two: yes, no, and not answered. A no is an answer that
 * a responder gave, so it must be distinguishable from a field they never reached. These tests pin
 * the stored and canonical form of all three readings, because the response surfaces rely on the
 * absent reading staying absent.
 */
class FieldValueBooleanTriStateTest
{
    private val registry = FieldTypeRegistry()

    private fun canon(input: kotlinx.serialization.json.JsonElement?) =
        registry.contractFor(FieldValueType.BOOLEAN)
            .canonicalize(input, FieldConstraints.EMPTY, emptyList())

    private fun storedValue(canonical: CanonicalFieldValue): FieldValue =
        FieldValue().apply {
            schemaAssignmentId = UUID.randomUUID()
            fieldContractId = UUID.randomUUID()
            resourceType = "EXCHANGE"
            resourceId = UUID.randomUUID()
            CanonicalValueCodec.applyTo(this, canonical)
        }

    @Test
    fun `an absent answer stores no reading and reads back as absent`()
    {
        listOf(null, JsonNull, JsonPrimitive("")).forEach { input ->
            val canonical = canon(input)
            assertTrue(canonical.isEmpty, "Input $input should leave the field unanswered")
            assertNull(canonical.boolValue)

            val stored = storedValue(canonical)
            assertNull(stored.boolValue)
            assertTrue(CanonicalValueCodec.isEmpty(stored, emptyList()))
            assertEquals(JsonNull, CanonicalValueCodec.toJson(stored, emptyList()))
        }
    }

    @Test
    fun `a no is an answer rather than an absence`()
    {
        val canonical = canon(JsonPrimitive(false))
        assertFalse(canonical.isEmpty)
        assertEquals(false, canonical.boolValue)

        val stored = storedValue(canonical)
        assertEquals(false, stored.boolValue)
        assertFalse(CanonicalValueCodec.isEmpty(stored, emptyList()))
        assertFalse(CanonicalValueCodec.toJson(stored, emptyList()).jsonPrimitive.boolean)
    }

    @Test
    fun `a yes is an answer`()
    {
        val stored = storedValue(canon(JsonPrimitive(true)))

        assertEquals(true, stored.boolValue)
        assertFalse(CanonicalValueCodec.isEmpty(stored, emptyList()))
        assertTrue(CanonicalValueCodec.toJson(stored, emptyList()).jsonPrimitive.boolean)
    }

    @Test
    fun `clearing an answered field returns it to unanswered`()
    {
        val stored = storedValue(canon(JsonPrimitive(false)))
        CanonicalValueCodec.applyTo(stored, canon(null))

        assertNull(stored.boolValue)
        assertTrue(CanonicalValueCodec.isEmpty(stored, emptyList()))
    }
}
