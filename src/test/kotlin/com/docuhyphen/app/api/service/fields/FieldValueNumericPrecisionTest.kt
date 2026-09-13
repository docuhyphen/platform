package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * A numeric answer must survive the round trip through storage and through the API without being
 * silently rounded. The stored column holds 28 integer and 10 fraction digits, so anything wider is
 * refused with a validation message instead of being reshaped by the database, and the canonical
 * JSON carries digits rather than a JSON number that a client would parse as a binary float.
 */
class FieldValueNumericPrecisionTest
{
    private val registry = FieldTypeRegistry()
    private val validator = FieldValueValidator(registry)

    private fun canon(
        type: FieldValueType,
        raw: String,
        constraints: FieldConstraints = FieldConstraints.EMPTY,
    ) = registry.contractFor(type).canonicalize(JsonPrimitive(raw), constraints, emptyList())

    private fun storedNumber(type: FieldValueType, stored: String): FieldValue =
        FieldValue().apply {
            schemaAssignmentId = UUID.randomUUID()
            fieldContractId = UUID.randomUUID()
            resourceType = "EXCHANGE"
            resourceId = UUID.randomUUID()
            valueType = type
            numberValue = BigDecimal(stored)
        }

    private fun numericContract(type: FieldValueType, constraintsJson: String): FieldContract =
        FieldContract().apply {
            fieldDefinitionId = UUID.randomUUID()
            valueType = type
            label = "Measured quantity"
            this.constraintsJson = constraintsJson
        }

    @Test
    fun `a decimal at the storable limit is kept digit for digit`()
    {
        val widest = "1234567890123456789012345678.0123456789"

        assertEquals(BigDecimal(widest), canon(FieldValueType.DECIMAL, widest).numberValue)
    }

    @Test
    fun `a decimal with more fraction digits than the store holds is refused`()
    {
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.DECIMAL, "1.12345678901")
        }
    }

    @Test
    fun `a number wider than the store holds is refused`()
    {
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.DECIMAL, "12345678901234567890123456789.5")
        }
        assertThrows(FieldValidationException::class.java) {
            canon(FieldValueType.INTEGER, "12345678901234567890123456789")
        }
    }

    @Test
    fun `trailing zeros are not counted as fraction digits`()
    {
        val padded = canon(FieldValueType.DECIMAL, "1.50000000000000")

        assertEquals(0, BigDecimal("1.5").compareTo(padded.numberValue))
    }

    @Test
    fun `canonical json carries digits rather than a json number`()
    {
        val exact = "1234567890123456789012345678.0123456789"
        val json = CanonicalValueCodec.toJson(storedNumber(FieldValueType.DECIMAL, exact), emptyList())

        assertTrue(json.jsonPrimitive.isString, "A number wider than a double must not be a JSON number")
        assertEquals(exact, json.jsonPrimitive.content)
    }

    @Test
    fun `column padding does not reach an integer's canonical text`()
    {
        val json = CanonicalValueCodec.toJson(storedNumber(FieldValueType.INTEGER, "5.0000000000"), emptyList())

        assertEquals("5", json.jsonPrimitive.content)
    }

    @Test
    fun `a decimal renders at the scale its contract configures`()
    {
        val json = CanonicalValueCodec.toJson(
            storedNumber(FieldValueType.DECIMAL, "1.5000000000"),
            emptyList(),
            numberScale = 2,
        )

        assertEquals("1.50", json.jsonPrimitive.content)
    }

    @Test
    fun `a decimal with no configured scale renders without column padding`()
    {
        val json = CanonicalValueCodec.toJson(storedNumber(FieldValueType.DECIMAL, "1.5000000000"), emptyList())

        assertEquals("1.5", json.jsonPrimitive.content)
    }

    @Test
    fun `a scale the store cannot hold is refused when the contract is configured`()
    {
        assertThrows(FieldValidationException::class.java) {
            validator.validateContractConfiguration(
                numericContract(FieldValueType.DECIMAL, """{"scale":12}"""),
            )
        }
        assertThrows(FieldValidationException::class.java) {
            validator.validateContractConfiguration(
                numericContract(FieldValueType.DECIMAL, """{"scale":-1}"""),
            )
        }
    }

    @Test
    fun `a storable scale is accepted when the contract is configured`()
    {
        validator.validateContractConfiguration(numericContract(FieldValueType.DECIMAL, """{"scale":10}"""))
        validator.validateContractConfiguration(numericContract(FieldValueType.DECIMAL, "{}"))
    }
}
