package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.workflow.WorkflowRequirementOperand
import com.docuhyphen.app.api.model.workflow.WorkflowTriggerSubject
import com.docuhyphen.app.api.service.fields.CanonicalFieldValue
import com.docuhyphen.app.api.service.fields.ExchangeFieldQueryService
import com.docuhyphen.app.api.service.fields.FieldOperator
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkflowRequirementConditionTest
{
    private val exchangeFields: ExchangeFieldQueryService = mock()
    private val operands: WorkflowRequirementOperandSource = mock()
    private val evaluator = WorkflowApplicabilityEvaluator(exchangeFields, FieldTypeRegistry(), operands)
    private val requestId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val packageId = UUID.randomUUID()
    private val templateRequirementId = UUID.randomUUID()

    @Test
    fun `a requirement condition reads the frozen answer of the exact package the trigger names`()
    {
        whenever(operands.frozenValue(requestId, packageId, templateRequirementId, "root"))
            .thenReturn(WorkflowRequirementOperand.Value(CanonicalFieldValue(FieldValueType.SHORT_TEXT, false, textValue = "Accepted")))

        assertTrue(applies(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("Accepted"))))
        assertFalse(applies(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("Returned"))))
    }

    @Test
    fun `a trigger that names no package reads nothing and the definition does not apply`()
    {
        assertFalse(
            evaluator.isApplicable(
                "INFORMATION_REQUEST",
                requestId,
                organizationId,
                condition(FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY, null),
                mapOf("requestId" to requestId.toString()),
            ),
        )
        verify(operands, never()).frozenValue(any(), any(), any(), any())
    }

    @Test
    fun `an ambiguous, absent, or foreign answer is a non-match rather than a guess`()
    {
        listOf("AMBIGUOUS", "ITEM_ABSENT", "PACKAGE_MISMATCH").forEach { reason ->
            whenever(operands.frozenValue(requestId, packageId, templateRequirementId, "root"))
                .thenReturn(WorkflowRequirementOperand.Unavailable(reason))
            assertFalse(applies(condition(FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY, null)))
        }
    }

    @Test
    fun `an unanswered item is empty and a value of another type never matches`()
    {
        whenever(operands.frozenValue(requestId, packageId, templateRequirementId, "root"))
            .thenReturn(WorkflowRequirementOperand.Empty)
        assertTrue(applies(condition(FieldValueType.INTEGER, FieldOperator.IS_EMPTY, null)))
        assertFalse(applies(condition(FieldValueType.INTEGER, FieldOperator.IS_NOT_EMPTY, null)))

        whenever(operands.frozenValue(requestId, packageId, templateRequirementId, "root"))
            .thenReturn(WorkflowRequirementOperand.Value(CanonicalFieldValue(FieldValueType.SHORT_TEXT, false, textValue = "4")))
        assertFalse(applies(condition(FieldValueType.INTEGER, FieldOperator.EQUALS, JsonPrimitive(4))))
    }

    @Test
    fun `requirement conditions never apply to an Exchange and Exchange field conditions never apply to a request`()
    {
        assertFalse(
            evaluator.isApplicable(
                "EXCHANGE",
                UUID.randomUUID(),
                organizationId,
                condition(FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY, null),
                subject(),
            ),
        )
        assertFalse(
            evaluator.isApplicable(
                "INFORMATION_REQUEST",
                requestId,
                organizationId,
                ApplicabilitySpec(
                    listOf(FieldConditionSpec(UUID.randomUUID().toString(), null, FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY)),
                ),
                subject(),
            ),
        )
        verifyNoInteractions(exchangeFields)
    }

    @Test
    fun `validation refuses a malformed requirement condition and one the trigger cannot scope to a package`()
    {
        val packageTrigger = WorkflowTriggerSubject("INFORMATION_REQUEST", setOf("requestId", "submissionPackageId"))
        evaluator.validate(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x")), packageTrigger)

        assertThrows<IllegalArgumentException> {
            evaluator.validate(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x"), requirement = "not-an-id"), packageTrigger)
        }
        assertThrows<IllegalArgumentException> {
            evaluator.validate(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x"), path = " "), packageTrigger)
        }
        assertThrows<IllegalArgumentException> {
            evaluator.validate(condition(FieldValueType.BOOLEAN, FieldOperator.GREATER_THAN, JsonPrimitive(true)), packageTrigger)
        }
        assertThrows<IllegalArgumentException> {
            evaluator.validate(condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, null), packageTrigger)
        }
        assertThrows<IllegalArgumentException> {
            evaluator.validate(
                condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x")),
                WorkflowTriggerSubject("INFORMATION_REQUEST", setOf("requestId")),
            )
        }
        assertThrows<IllegalArgumentException> {
            evaluator.validate(
                condition(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("x")),
                WorkflowTriggerSubject("EXCHANGE", setOf("initiatorId")),
            )
        }
        assertThrows<IllegalArgumentException> {
            evaluator.validate(
                ApplicabilitySpec(
                    listOf(FieldConditionSpec(UUID.randomUUID().toString(), null, FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY)),
                ),
                packageTrigger,
            )
        }
    }

    private fun applies(spec: ApplicabilitySpec): Boolean =
        evaluator.isApplicable("INFORMATION_REQUEST", requestId, organizationId, spec, subject())

    private fun subject(): Map<String, String> = mapOf(
        "requestId" to requestId.toString(),
        "submissionPackageId" to packageId.toString(),
    )

    private fun condition(
        type: FieldValueType,
        operator: FieldOperator,
        value: JsonElement?,
        requirement: String = templateRequirementId.toString(),
        path: String = "root",
    ) = ApplicabilitySpec(
        requirementConditions = listOf(RequirementConditionSpec(requirement, path, "recorded-note", type, operator, value)),
    )
}
