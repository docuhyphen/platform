package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.command.model.UnstatedFingerprintValue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

class CommandRequestFingerprintTest {
    @Test
    fun `fields that only move a separator between them have different fingerprints`() {
        assertNotEquals(
            CommandRequestFingerprint.of("operation", "reason|segment", "record"),
            CommandRequestFingerprint.of("operation", "reason", "segment|record"),
        )
        assertNotEquals(
            CommandRequestFingerprint.of("operation", "source,zone", "area"),
            CommandRequestFingerprint.of("operation", "source", "zone,area"),
        )
        assertNotEquals(
            CommandRequestFingerprint.of("operation", "a\",\"b"),
            CommandRequestFingerprint.of("operation", "a", "b"),
        )
    }

    @Test
    fun `an absent field, an empty field, and a field holding the word null have different fingerprints`() {
        val fingerprints = setOf(
            CommandRequestFingerprint.of("operation", null),
            CommandRequestFingerprint.of("operation", ""),
            CommandRequestFingerprint.of("operation", "null"),
            CommandRequestFingerprint.of("operation"),
        )

        assertEquals(4, fingerprints.size)
    }

    @Test
    fun `a nested list keeps the boundaries of its own items`() {
        assertNotEquals(
            CommandRequestFingerprint.of("operation", listOf("a", "b"), "c"),
            CommandRequestFingerprint.of("operation", listOf("a"), "b", "c"),
        )
        assertNotEquals(
            CommandRequestFingerprint.of("operation", listOf("a,b")),
            CommandRequestFingerprint.of("operation", listOf("a", "b")),
        )
        assertNotEquals(
            CommandRequestFingerprint.of("operation", listOf("a", "b")),
            CommandRequestFingerprint.of("operation", listOf("b", "a")),
        )
    }

    @Test
    fun `the same fields in the same order always have the same fingerprint`() {
        val id = UUID.randomUUID()
        val at = Instant.parse("2026-10-12T10:15:30Z")
        val fields = arrayOf<Any?>(
            "operation", id, PrincipalKind.USER, 3, 4L, true, at, LocalDate.parse("2026-10-12"),
            Timestamp.from(at), BigDecimal("12.50"), null, listOf("a", null), mapOf("b" to 1, "a" to "x"),
        )

        assertEquals(CommandRequestFingerprint.of(*fields), CommandRequestFingerprint.of(*fields))
        assertEquals(CommandRequestFingerprint.of(at), CommandRequestFingerprint.of(Timestamp.from(at)))
        assertEquals(64, CommandRequestFingerprint.of(*fields).length)
    }

    @Test
    fun `field order is part of the fingerprint`() {
        assertNotEquals(CommandRequestFingerprint.of("a", "b"), CommandRequestFingerprint.of("b", "a"))
    }

    @Test
    fun `a number and the text of that number have different fingerprints`() {
        assertNotEquals(CommandRequestFingerprint.of("operation", 1), CommandRequestFingerprint.of("operation", "1"))
        assertNotEquals(
            CommandRequestFingerprint.of("operation", true),
            CommandRequestFingerprint.of("operation", "true")
        )
    }

    @Test
    fun `a map or a set gets the same fingerprint whatever order its entries were added in`() {
        assertEquals(
            CommandRequestFingerprint.of(linkedMapOf("b" to 1, "a" to 2)),
            CommandRequestFingerprint.of(linkedMapOf("a" to 2, "b" to 1)),
        )
        assertEquals(
            CommandRequestFingerprint.of(linkedSetOf("b", "a")),
            CommandRequestFingerprint.of(linkedSetOf("a", "b")),
        )
        assertEquals(
            CommandRequestFingerprint.of(buildJsonObject { put("b", JsonPrimitive(1)); put("a", JsonPrimitive(2)) }),
            CommandRequestFingerprint.of(buildJsonObject { put("a", JsonPrimitive(2)); put("b", JsonPrimitive(1)) }),
        )
    }

    @Test
    fun `a value whose type states no canonical form is refused rather than printed`() {
        assertThrows<IllegalArgumentException> {
            CommandRequestFingerprint.of(
                "operation",
                UnstatedFingerprintValue("a|b")
            )
        }
        assertThrows<IllegalArgumentException> { CommandRequestFingerprint.of("operation", 1.5) }
    }
}
