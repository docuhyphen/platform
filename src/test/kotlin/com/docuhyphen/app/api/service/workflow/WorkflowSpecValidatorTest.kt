package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry
import com.docuhyphen.app.api.repository.WorkflowTriggerEventRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WorkflowSpecValidatorTest
{
    private val triggerRepository: WorkflowTriggerEventRepository = mock()
    private val validator = WorkflowSpecValidator(
        triggerRepository,
        ConditionPredicateService(),
        mock(),
        mock(),
    )

    @Test
    fun `accepts predicates compatible with the trigger registry`()
    {
        registerFields()

        assertDoesNotThrow {
            validator.validatePredicates(spec("\$subject.amount >= -1.5"), TRIGGER)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "\$subject.amount contains '1'",
        "\$subject.unknown == 'value'",
        "\$subject.name > 'value'",
        "\$subject.name == 'unterminated",
        " ",
    ])
    fun `rejects malformed unknown and incompatible predicates with a stable path`(expression: String)
    {
        registerFields()

        val error = assertThrows(WorkflowSpecValidationException::class.java) {
            validator.validatePredicates(spec(expression), TRIGGER)
        }.errors.single()

        assertEquals(0, error.stepIndex)
        assertEquals("steps[0].predicateExpression", error.fieldPath)
        assertEquals(true, error.code.startsWith("WORKFLOW_CONDITION_"))
    }

    @Test
    fun `save-time validation and runtime evaluation use the same predicate contract`()
    {
        registerFields()
        val expression = "\$subject.name startsWith 'Docu'"

        assertDoesNotThrow { validator.validatePredicates(spec(expression), TRIGGER) }
        assertEquals(
            PredicateResult.Valid(true),
            ConditionPredicateService().evaluate(
                expression,
                listOf(WorkflowSubjectField("name", "STRING"), WorkflowSubjectField("amount", "number")),
                mapOf("name" to "DocuHyphen"),
            ),
        )
    }

    private fun registerFields()
    {
        whenever(triggerRepository.findByEventName(TRIGGER)).thenReturn(
            WorkflowTriggerEventRegistry().apply {
                eventName = TRIGGER
                subjectFieldsJson = """[{"name":"name","type":"STRING"},{"name":"amount","type":"number"}]"""
            },
        )
    }

    private fun spec(expression: String): WorkflowSpec = WorkflowSpec(
        steps = listOf(
            WorkflowStepSpec(
                type = WorkflowStepType.CONDITION,
                predicateExpression = expression,
                onTrue = StepOutcomeSpec("END"),
                onFalse = StepOutcomeSpec("END"),
            ),
        ),
    )

    private companion object
    {
        const val TRIGGER = "exchange.test"
    }
}
