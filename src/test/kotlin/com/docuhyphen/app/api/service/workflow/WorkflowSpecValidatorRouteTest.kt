package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.WorkflowTriggerEventRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock

/**
 * Verifies that step outcome routing targets are rejected when they reference a step index that no
 * longer exists or is otherwise not a terminal `END` or an in-range index. This guards against the
 * dangling references that a designer step deletion could previously leave behind.
 */
class WorkflowSpecValidatorRouteTest
{
    private val triggerRepository: WorkflowTriggerEventRepository = mock()
    private val validator = WorkflowSpecValidator(
        triggerRepository,
        ConditionPredicateService(),
        mock(),
        mock(),
    )

    @Test
    fun `accepts END and in-range routing targets`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                approval(onApprove = "1", onReject = "END"),
                approval(onApprove = "END", onReject = "0"),
            ),
        )

        assertDoesNotThrow { validator.validateRoutes(spec) }
    }

    @Test
    fun `accepts a spec with no configured outcomes`()
    {
        val spec = WorkflowSpec(steps = listOf(WorkflowStepSpec(type = WorkflowStepType.NOTIFICATION)))

        assertDoesNotThrow { validator.validateRoutes(spec) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["2", "5", "-1", "abc", "1.0", " 0", ""])
    fun `rejects out-of-range and malformed routing targets with a stable path`(target: String)
    {
        val spec = WorkflowSpec(
            steps = listOf(
                approval(onApprove = target, onReject = "END"),
                approval(onApprove = "END", onReject = "END"),
            ),
        )

        val error = assertThrows(WorkflowSpecValidationException::class.java) {
            validator.validateRoutes(spec)
        }.errors.single()

        assertEquals("WORKFLOW_ROUTE_INVALID_TARGET", error.code)
        assertEquals(0, error.stepIndex)
        assertEquals("steps[0].onApprove.nextStep", error.fieldPath)
    }

    @Test
    fun `reports every invalid outcome across condition branches`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                WorkflowStepSpec(
                    type = WorkflowStepType.CONDITION,
                    onTrue = StepOutcomeSpec("9"),
                    onFalse = StepOutcomeSpec("7"),
                ),
            ),
        )

        val errors = assertThrows(WorkflowSpecValidationException::class.java) {
            validator.validateRoutes(spec)
        }.errors

        assertEquals(2, errors.size)
        assertEquals(
            setOf("steps[0].onTrue.nextStep", "steps[0].onFalse.nextStep"),
            errors.map { it.fieldPath }.toSet(),
        )
    }

    private fun approval(onApprove: String, onReject: String) = WorkflowStepSpec(
        type = WorkflowStepType.APPROVAL,
        onApprove = StepOutcomeSpec(onApprove),
        onReject = StepOutcomeSpec(onReject),
    )
}
