package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.fields.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class WorkflowApplicabilityEvaluatorTest
{
    private val queryService: ExchangeFieldQueryService = mock()
    private val evaluator = WorkflowApplicabilityEvaluator(queryService, FieldTypeRegistry())

    private val exchangeId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val fieldId = UUID.randomUUID()

    private fun snapshotOf(vararg values: Pair<UUID, CanonicalFieldValue>) =
        ExchangeFieldSnapshot(hasAssignedSchema = true, valuesByFieldDefinitionId = values.toMap())

    private fun condition(
        type: FieldValueType,
        operator: FieldOperator,
        value: kotlinx.serialization.json.JsonElement? = null,
        field: UUID = fieldId,
    ) = ApplicabilitySpec(
        listOf(FieldConditionSpec(field.toString(), null, type, operator, value)),
    )

    private fun isApplicable(spec: ApplicabilitySpec?): Boolean =
        evaluator.isApplicable(ResourceType.EXCHANGE.name, exchangeId, orgId, spec)

    // ── Empty / structural ─────────────────────────────────────────────────────

    @Test
    fun `null applicability is always applicable`()
    {
        assertTrue(evaluator.isApplicable(ResourceType.EXCHANGE.name, exchangeId, orgId, null))
    }

    @Test
    fun `empty condition list is always applicable`()
    {
        assertTrue(isApplicable(ApplicabilitySpec(emptyList())))
    }

    @Test
    fun `non-Exchange subject with conditions is non-match`()
    {
        val spec = condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x"))
        assertFalse(evaluator.isApplicable("DOCUMENT", exchangeId, orgId, spec))
    }

    @Test
    fun `missing schema is non-match`()
    {
        whenever(queryService.getCanonicalValues(exchangeId))
            .thenReturn(ExchangeFieldSnapshot(false, emptyMap()))
        assertFalse(isApplicable(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x"))))
    }

    @Test
    fun `missing value is non-match`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(snapshotOf())
        assertFalse(isApplicable(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x"))))
    }

    // ── Text ───────────────────────────────────────────────────────────────────

    @Test
    fun `text equals matches`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.SHORT_TEXT, false, textValue = "Onboarding")),
        )
        assertTrue(isApplicable(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("Onboarding"))))
        assertFalse(isApplicable(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("Other"))))
    }

    @Test
    fun `text contains and starts_with`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.LONG_TEXT, false, textValue = "hello world")),
        )
        assertTrue(isApplicable(condition(FieldValueType.LONG_TEXT, FieldOperator.CONTAINS, JsonPrimitive("lo wo"))))
        assertTrue(isApplicable(condition(FieldValueType.LONG_TEXT, FieldOperator.STARTS_WITH, JsonPrimitive("hello"))))
        assertFalse(isApplicable(condition(FieldValueType.LONG_TEXT, FieldOperator.STARTS_WITH, JsonPrimitive("world"))))
    }

    // ── Number ─────────────────────────────────────────────────────────────────

    @Test
    fun `number comparisons`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.INTEGER, false, numberValue = BigDecimal("10"))),
        )
        assertTrue(isApplicable(condition(FieldValueType.INTEGER, FieldOperator.GREATER_THAN, JsonPrimitive(5))))
        assertTrue(isApplicable(condition(FieldValueType.INTEGER, FieldOperator.LESS_THAN_OR_EQUAL, JsonPrimitive(10))))
        assertFalse(isApplicable(condition(FieldValueType.INTEGER, FieldOperator.LESS_THAN, JsonPrimitive(10))))
    }

    // ── Date-time ──────────────────────────────────────────────────────────────

    @Test
    fun `date-time comparisons use the moment rather than the wall clock`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(
                fieldId to CanonicalFieldValue(
                    FieldValueType.DATE_TIME,
                    false,
                    datetimeValue = Instant.parse("2026-08-31T08:15:30Z"),
                    datetimeOffsetMinutes = 120,
                ),
            ),
        )

        // The stored answer was written as 10:15:30+02:00, so a condition naming the same moment at
        // any offset must match and one naming a different moment must not.
        assertTrue(
            isApplicable(
                condition(FieldValueType.DATE_TIME, FieldOperator.EQUALS, JsonPrimitive("2026-08-31T08:15:30Z")),
            ),
        )
        assertTrue(
            isApplicable(
                condition(FieldValueType.DATE_TIME, FieldOperator.EQUALS, JsonPrimitive("2026-08-31T03:15:30-05:00")),
            ),
        )
        assertFalse(
            isApplicable(
                condition(FieldValueType.DATE_TIME, FieldOperator.EQUALS, JsonPrimitive("2026-08-31T10:15:30Z")),
            ),
        )
        assertTrue(
            isApplicable(
                condition(FieldValueType.DATE_TIME, FieldOperator.LESS_THAN, JsonPrimitive("2026-08-31T10:15:30Z")),
            ),
        )
    }

    @Test
    fun `a date-time literal with no offset is read as UTC`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(
                fieldId to CanonicalFieldValue(
                    FieldValueType.DATE_TIME,
                    false,
                    datetimeValue = Instant.parse("2026-08-31T08:15:30Z"),
                ),
            ),
        )

        assertTrue(
            isApplicable(
                condition(FieldValueType.DATE_TIME, FieldOperator.EQUALS, JsonPrimitive("2026-08-31T08:15:30")),
            ),
        )
        assertFalse(
            isApplicable(
                condition(FieldValueType.DATE_TIME, FieldOperator.EQUALS, JsonPrimitive("2026-08-31T10:15:30")),
            ),
        )
    }

    // ── Boolean ────────────────────────────────────────────────────────────────

    @Test
    fun `boolean equals`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.BOOLEAN, false, boolValue = true)),
        )
        assertTrue(isApplicable(condition(FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive(true))))
        assertFalse(isApplicable(condition(FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive(false))))
    }

    // ── Select ─────────────────────────────────────────────────────────────────

    @Test
    fun `single select equals matches option code`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.SINGLE_SELECT, false, selectionCodes = listOf("ONB"))),
        )
        assertTrue(isApplicable(condition(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive("ONB"))))
        assertFalse(isApplicable(condition(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive("OTHER"))))
    }

    @Test
    fun `single select IN`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.SINGLE_SELECT, false, selectionCodes = listOf("ONB"))),
        )
        val inList = JsonArray(listOf(JsonPrimitive("ONB"), JsonPrimitive("KYC")))
        assertTrue(isApplicable(condition(FieldValueType.SINGLE_SELECT, FieldOperator.IN, inList)))
    }

    @Test
    fun `multi select contains`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue(FieldValueType.MULTI_SELECT, false, selectionCodes = listOf("A", "B"))),
        )
        assertTrue(isApplicable(condition(FieldValueType.MULTI_SELECT, FieldOperator.CONTAINS, JsonPrimitive("B"))))
        assertFalse(isApplicable(condition(FieldValueType.MULTI_SELECT, FieldOperator.CONTAINS, JsonPrimitive("C"))))
    }

    @Test
    fun `retired option no longer stored is non-match`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue.empty(FieldValueType.SINGLE_SELECT)),
        )
        assertFalse(isApplicable(condition(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive("ONB"))))
    }

    // ── Emptiness operators ──────────────────────────────────────────────────────

    @Test
    fun `is_empty and is_not_empty`()
    {
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(fieldId to CanonicalFieldValue.empty(FieldValueType.SHORT_TEXT)),
        )
        assertTrue(isApplicable(condition(FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY)))
        assertFalse(isApplicable(condition(FieldValueType.SHORT_TEXT, FieldOperator.IS_NOT_EMPTY)))
    }

    // ── AND semantics ────────────────────────────────────────────────────────────

    @Test
    fun `all conditions must match`()
    {
        val second = UUID.randomUUID()
        whenever(queryService.getCanonicalValues(exchangeId)).thenReturn(
            snapshotOf(
                fieldId to CanonicalFieldValue(FieldValueType.SHORT_TEXT, false, textValue = "A"),
                second to CanonicalFieldValue(FieldValueType.INTEGER, false, numberValue = BigDecimal("3")),
            ),
        )
        val spec = ApplicabilitySpec(
            listOf(
                FieldConditionSpec(fieldId.toString(), null, FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("A")),
                FieldConditionSpec(second.toString(), null, FieldValueType.INTEGER, FieldOperator.GREATER_THAN, JsonPrimitive(5)),
            ),
        )
        assertFalse(isApplicable(spec))
    }

    // ── Validation ───────────────────────────────────────────────────────────────

    @Test
    fun `validate rejects unsupported operator for type`()
    {
        val spec = condition(FieldValueType.BOOLEAN, FieldOperator.GREATER_THAN, JsonPrimitive(1))
        assertThrows(IllegalArgumentException::class.java) { evaluator.validate(spec) }
    }

    @Test
    fun `validate rejects missing value for value operator`()
    {
        val spec = condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, null)
        assertThrows(IllegalArgumentException::class.java) { evaluator.validate(spec) }
    }

    @Test
    fun `validate rejects invalid field id`()
    {
        val spec = ApplicabilitySpec(
            listOf(FieldConditionSpec("not-a-uuid", null, FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY, null)),
        )
        assertThrows(IllegalArgumentException::class.java) { evaluator.validate(spec) }
    }

    @Test
    fun `validate accepts a valid spec`()
    {
        val spec = condition(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive("ONB"))
        evaluator.validate(spec)
    }
}
