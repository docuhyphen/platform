package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FieldTypeContractTest
{
    private val registry = FieldTypeRegistry()

    private fun canon(
        type: FieldValueType,
        input: kotlinx.serialization.json.JsonElement?,
        constraints: FieldConstraints = FieldConstraints.EMPTY,
        options: List<FieldOption> = emptyList(),
    ) = registry.contractFor(type).canonicalize(input, constraints, options)

    @Test
    fun `registry has a contract for every declared type`()
    {
        assertEquals(FieldValueType.entries.toSet(), registry.supportedTypes())
    }

    @Test
    fun `short text trims and enforces length`()
    {
        val result = canon(FieldValueType.SHORT_TEXT, JsonPrimitive("  hello  "))
        assertEquals("hello", result.textValue)

        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.SHORT_TEXT, JsonPrimitive("ab"), FieldConstraints(minLength = 3))
        }
    }

    @Test
    fun `null and blank inputs are empty`()
    {
        assertTrue(canon(FieldValueType.SHORT_TEXT, null).isEmpty)
        assertTrue(canon(FieldValueType.SHORT_TEXT, JsonNull).isEmpty)
        assertTrue(canon(FieldValueType.SHORT_TEXT, JsonPrimitive("   ")).isEmpty)
    }

    @Test
    fun `boolean accepts common truthy forms`()
    {
        assertEquals(true, canon(FieldValueType.BOOLEAN, JsonPrimitive("yes")).boolValue)
        assertEquals(false, canon(FieldValueType.BOOLEAN, JsonPrimitive(false)).boolValue)
    }

    @Test
    fun `integer rejects fractional and enforces range`()
    {
        assertEquals(BigDecimal("5"), canon(FieldValueType.INTEGER, JsonPrimitive("5")).numberValue)
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.INTEGER, JsonPrimitive("5.5"))
        }
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.INTEGER, JsonPrimitive("2"), FieldConstraints(minValue = "3"))
        }
    }

    @Test
    fun `decimal enforces scale`()
    {
        val ok = canon(FieldValueType.DECIMAL, JsonPrimitive("1.5"), FieldConstraints(scale = 2))
        assertEquals(BigDecimal("1.50"), ok.numberValue)
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.DECIMAL, JsonPrimitive("1.555"), FieldConstraints(scale = 2))
        }
    }

    @Test
    fun `date parses ISO and enforces bounds`()
    {
        val r = canon(FieldValueType.DATE, JsonPrimitive("2026-01-15"))
        assertEquals("2026-01-15", r.dateValue.toString())
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.DATE, JsonPrimitive("not-a-date"))
        }
    }

    @Test
    fun `single select validates against active options`()
    {
        val options = listOf(
            FieldOption(code = "A", label = "Alpha"),
            FieldOption(code = "B", label = "Beta", active = false),
        )
        assertEquals(
            listOf("A"),
            canon(FieldValueType.SINGLE_SELECT, JsonPrimitive("A"), options = options).selectionCodes,
        )
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.SINGLE_SELECT, JsonPrimitive("B"), options = options)
        }
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.SINGLE_SELECT, JsonPrimitive("Z"), options = options)
        }
    }

    @Test
    fun `multi select dedupes, orders by option order, and enforces bounds`()
    {
        val options = listOf(
            FieldOption(code = "A", label = "Alpha", order = 0),
            FieldOption(code = "B", label = "Beta", order = 1),
            FieldOption(code = "C", label = "Gamma", order = 2),
        )
        val input = buildJsonArray { add("C"); add("A"); add("A") }
        val result = canon(FieldValueType.MULTI_SELECT, input, options = options)
        assertEquals(listOf("A", "C"), result.selectionCodes)

        assertThrows(FieldValidationException::class.java) {
            canon(
                FieldValueType.MULTI_SELECT,
                buildJsonArray { add("A"); add("B"); add("C") },
                FieldConstraints(maxSelections = 2),
                options,
            )
        }
    }

    @Test
    fun `contract configuration requires options for select types`()
    {
        val validator = FieldValueValidator(registry)
        val contract = com.docuhyphen.app.api.model.entity.FieldContract().apply {
            fieldDefinitionId = java.util.UUID.randomUUID()
            valueType = FieldValueType.SINGLE_SELECT
            label = "Status"
            optionsJson = "[]"
        }
        assertThrows(FieldValidationException::class.java) {
            validator.validateContractConfiguration(contract)
        }
    }
}
