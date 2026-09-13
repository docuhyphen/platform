package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * A date-time answer names one moment. Reading it back must yield that moment regardless of the
 * offset it was written at, and the offset the responder submitted must survive so the answer can
 * be shown as it was given.
 */
class FieldValueOffsetDateTimeTest
{
    private val registry = FieldTypeRegistry()

    private fun canon(raw: String) = registry.contractFor(FieldValueType.DATE_TIME)
        .canonicalize(JsonPrimitive(raw), FieldConstraints.EMPTY, emptyList())

    private fun storedValue(canonical: CanonicalFieldValue): FieldValue =
        FieldValue().apply {
            schemaAssignmentId = UUID.randomUUID()
            fieldContractId = UUID.randomUUID()
            resourceType = "EXCHANGE"
            resourceId = UUID.randomUUID()
            CanonicalValueCodec.applyTo(this, canonical)
        }

    @Test
    fun `the same moment written at two offsets canonicalizes to one instant`()
    {
        val atPlusTwo = canon("2026-08-31T10:15:30+02:00")
        val atUtc = canon("2026-08-31T08:15:30Z")
        val atMinusFive = canon("2026-08-31T03:15:30-05:00")

        assertEquals(Instant.parse("2026-08-31T08:15:30Z"), atUtc.datetimeValue)
        assertEquals(atUtc.datetimeValue, atPlusTwo.datetimeValue)
        assertEquals(atUtc.datetimeValue, atMinusFive.datetimeValue)
    }

    @Test
    fun `the submitted offset is recorded next to the instant`()
    {
        assertEquals(120, canon("2026-08-31T10:15:30+02:00").datetimeOffsetMinutes)
        assertEquals(-300, canon("2026-08-31T03:15:30-05:00").datetimeOffsetMinutes)
        assertEquals(0, canon("2026-08-31T08:15:30Z").datetimeOffsetMinutes)
        assertEquals(330, canon("2026-08-31T13:45:30+05:30").datetimeOffsetMinutes)
    }

    @Test
    fun `a reading with no offset is taken as UTC and records no offset`()
    {
        val local = canon("2026-08-31T08:15:30")

        assertEquals(Instant.parse("2026-08-31T08:15:30Z"), local.datetimeValue)
        assertNull(local.datetimeOffsetMinutes)
    }

    @Test
    fun `an offset in basic form is accepted`()
    {
        assertEquals(Instant.parse("2026-08-31T08:15:30Z"), canon("2026-08-31T10:15:30+0200").datetimeValue)
        assertEquals(120, canon("2026-08-31T10:15:30+0200").datetimeOffsetMinutes)
        assertEquals(Instant.parse("2026-08-31T08:15:30Z"), canon("2026-08-31T10:15:30+02").datetimeValue)
    }

    @Test
    fun `canonical json renders the instant at the submitted offset`()
    {
        val stored = storedValue(canon("2026-08-31T10:15:30+02:00"))

        assertEquals(
            "2026-08-31T10:15:30+02:00",
            CanonicalValueCodec.toJson(stored, emptyList()).jsonPrimitive.content,
        )
    }

    @Test
    fun `canonical json renders a reading with no submitted offset as UTC`()
    {
        val stored = storedValue(canon("2026-08-31T08:15:30"))

        assertEquals(
            "2026-08-31T08:15:30Z",
            CanonicalValueCodec.toJson(stored, emptyList()).jsonPrimitive.content,
        )
    }

    @Test
    fun `a canonical rendering re-reads as the same instant and offset`()
    {
        val first = canon("2026-08-31T13:45:30.123456+05:30")
        val rendered = CanonicalValueCodec.toJson(storedValue(first), emptyList()).jsonPrimitive.content
        val second = canon(rendered)

        assertEquals(first.datetimeValue, second.datetimeValue)
        assertEquals(first.datetimeOffsetMinutes, second.datetimeOffsetMinutes)
    }

    @Test
    fun `an unreadable date-time is refused`()
    {
        assertThrows(FieldValidationException::class.java) { canon("31-08-2026 10:15") }
        assertThrows(FieldValidationException::class.java) { canon("2026-08-31T10:15:30+99:00") }
    }

    @Test
    fun `a blank date-time is empty rather than an instant`()
    {
        val blank = canon("   ")

        assertTrue(blank.isEmpty)
        assertNull(blank.datetimeValue)
        assertNull(blank.datetimeOffsetMinutes)
    }
}
