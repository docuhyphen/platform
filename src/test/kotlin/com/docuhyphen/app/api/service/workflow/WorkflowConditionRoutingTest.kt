package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.entity.WorkflowTransitionOutcome
import com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowStepTransitionRepository
import com.docuhyphen.app.api.repository.WorkflowTriggerEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkflowConditionRoutingTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val triggerRepository: WorkflowTriggerEventRepository = mock()

    @ParameterizedTest
    @CsvSource(
        "'\$subject.amount >= 10', 12, TRUE",
        "'\$subject.amount >= 10', 8, FALSE",
        "'\$subject.amount >= broken', 12, FALSE",
        "'\$subject.missing != ''value''', ignored, FALSE",
        "' ', 12, FALSE",
    )
    fun `condition execution records the selected fail-closed branch`(
        expression: String,
        actual: String,
        expectedOutcome: WorkflowTransitionOutcome,
    )
    {
        val definitionId = UUID.randomUUID()
        val definition = WorkflowDefinition().apply {
            id = definitionId
            name = "Condition routing"
            triggerEvent = TRIGGER
            stepsJson = WorkflowSpecJson.encode(WorkflowSpec())
        }
        whenever(definitionRepository.findById(definitionId)).thenReturn(definition)
        whenever(triggerRepository.findByEventName(TRIGGER)).thenReturn(
            WorkflowTriggerEventRegistry().apply {
                eventName = TRIGGER
                subjectFieldsJson = """[{"name":"amount","type":"number"}]"""
            },
        )
        whenever(transitionRepository.existsByFromStepInstanceId(any())).thenReturn(false)

        val instance = WorkflowInstance().apply {
            id = UUID.randomUUID()
            this.definitionId = definitionId
            status = WorkflowInstanceStatus.RUNNING
            // Condition steps resolve their subject-field registry from the frozen trigger.
            triggerEventSnapshot = TRIGGER
            subjectDataJson = """{"amount":"$actual"}"""
        }
        val step = WorkflowStepInstance().apply {
            id = UUID.randomUUID()
            instanceId = instance.id
            stepIndex = 0
            stepType = WorkflowStepType.CONDITION
            status = WorkflowStepStatus.PENDING
        }
        val spec = WorkflowStepSpec(
            type = WorkflowStepType.CONDITION,
            predicateExpression = expression,
            onTrue = StepOutcomeSpec("END"),
            onFalse = StepOutcomeSpec("END"),
        )

        executeCondition(newEngine(), instance, step, spec)

        val transition = argumentCaptor<com.docuhyphen.app.api.model.entity.WorkflowStepTransition>()
        org.mockito.kotlin.verify(transitionRepository).save(transition.capture())
        assertEquals(expectedOutcome, transition.firstValue.outcome)
        assertEquals(WorkflowStepStatus.COMPLETED, step.status)
        assertEquals(WorkflowInstanceStatus.COMPLETED, instance.status)
    }

    private fun newEngine(): DefaultWorkflowEngineService = DefaultWorkflowEngineService().also { engine ->
        inject(engine, "definitionRepository", definitionRepository)
        inject(engine, "transitionRepository", transitionRepository)
        inject(engine, "triggerEventRepository", triggerRepository)
        inject(engine, "conditionPredicateService", ConditionPredicateService())
    }

    private fun inject(engine: DefaultWorkflowEngineService, fieldName: String, value: Any)
    {
        DefaultWorkflowEngineService::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(engine, value)
        }
    }

    private fun executeCondition(
        engine: DefaultWorkflowEngineService,
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
    )
    {
        DefaultWorkflowEngineService::class.java.getDeclaredMethod(
            "executeConditionStep",
            WorkflowInstance::class.java,
            WorkflowStepInstance::class.java,
            WorkflowStepSpec::class.java,
        ).apply {
            isAccessible = true
            invoke(engine, instance, step, spec)
        }
    }

    private companion object
    {
        const val TRIGGER = "exchange.condition_test"
    }
}
