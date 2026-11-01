package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.workflow.StepOutcomeSpec
import com.docuhyphen.app.api.model.workflow.TriggerRequest
import com.docuhyphen.app.api.model.workflow.WorkflowSpec
import com.docuhyphen.app.api.model.workflow.WorkflowStepSpec
import com.docuhyphen.app.api.repository.workflow.*
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class WorkflowInstanceGraphRecordingTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val assigneeRepository: WorkflowStepAssigneeRepository = mock()
    private val decisionRepository: WorkflowStepDecisionRepository = mock()
    private val assigneeResolver: WorkflowAssigneeResolver = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val applicabilityEvaluator: WorkflowApplicabilityEvaluator = mock()

    private val definitionId = UUID.randomUUID()
    private val instanceId = UUID.randomUUID()

    private fun newEngine(): DefaultWorkflowEngineService
    {
        val engine = DefaultWorkflowEngineService()
        inject(engine, "definitionRepository", definitionRepository)
        inject(engine, "instanceRepository", instanceRepository)
        inject(engine, "stepRepository", stepRepository)
        inject(engine, "transitionRepository", transitionRepository)
        inject(engine, "assigneeRepository", assigneeRepository)
        inject(engine, "decisionRepository", decisionRepository)
        inject(engine, "assigneeResolver", assigneeResolver)
        inject(engine, "eventPublisher", eventPublisher)
        inject(engine, "applicabilityEvaluator", applicabilityEvaluator)
        inject(engine, "subscriptionGuard", mock<WorkflowSubscriptionGuard>())
        return engine
    }

    private fun inject(target: Any, field: String, value: Any)
    {
        val f = DefaultWorkflowEngineService::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(target, value)
    }

    private fun approvalStep(nextStep: String): WorkflowStepSpec =
        WorkflowStepSpec(type = WorkflowStepType.APPROVAL, onApprove = StepOutcomeSpec(nextStep))

    private fun definitionWith(vararg steps: WorkflowStepSpec): WorkflowDefinition =
        WorkflowDefinition().apply {
            id = definitionId
            name = "Test workflow"
            version = 3
            triggerEvent = "test.event"
            stepsJson = WorkflowSpecJson.encode(WorkflowSpec(steps = steps.toList()))
        }

    private fun runningInstance(vararg steps: WorkflowStepSpec): WorkflowInstance =
        WorkflowInstance().apply {
            id = instanceId
            definitionId = this@WorkflowInstanceGraphRecordingTest.definitionId
            definitionVersion = 3
            status = WorkflowInstanceStatus.RUNNING
            currentStepIndex = 0
            triggerEventSnapshot = "test.event"
            definitionSnapshotJson = WorkflowSpecJson.encode(
                WorkflowSpec(steps = if (steps.isEmpty()) listOf(approvalStep("END")) else steps.toList()),
            )
        }

    private fun stepInstance(index: Int, spec: WorkflowStepSpec): WorkflowStepInstance =
        WorkflowStepInstance().apply {
            id = UUID.randomUUID()
            instanceId = this@WorkflowInstanceGraphRecordingTest.instanceId
            stepIndex = index
            stepType = spec.type
            status = WorkflowStepStatus.PENDING
            specSnapshotJson = WorkflowSpecJson.encodeStep(spec)
        }

    private fun assigneeFor(ref: PrincipalRef): WorkflowStepAssignee =
        WorkflowStepAssignee().apply {
            stepInstanceId = UUID.randomUUID()
            principalKind = ref.kind
            principalId = ref.id
        }

    private fun approveDecision(): WorkflowStepDecision =
        WorkflowStepDecision().apply {
            principalKind = PrincipalKind.USER
            principalId = UUID.randomUUID()
            decision = Decision.APPROVE.name
        }

    @Test
    fun `trigger freezes the definition snapshot and records the START edge`()
    {
        val definition = definitionWith(approvalStep("END"))
        whenever(definitionRepository.findAllActiveForTrigger(any(), any())).thenReturn(listOf(definition))
        whenever(applicabilityEvaluator.isApplicable(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(true)
        whenever(assigneeResolver.resolveAll(any(), any())).thenReturn(emptyList())
        whenever(transitionRepository.existsStartByInstanceId(any())).thenReturn(false)

        newEngine().trigger(
            TriggerRequest(triggerEvent = "test.event", organizationId = UUID.randomUUID()),
        )

        val instanceCaptor = argumentCaptor<WorkflowInstance>()
        verify(instanceRepository).save(instanceCaptor.capture())
        assertEquals(definition.stepsJson, instanceCaptor.firstValue.definitionSnapshotJson)

        val txn = argumentCaptor<WorkflowStepTransition>()
        verify(transitionRepository).save(txn.capture())
        assertNull(txn.firstValue.fromStepInstanceId)
        assertNull(txn.firstValue.fromStepIndex)
        assertEquals(0, txn.firstValue.toStepIndex)
        assertEquals(WorkflowTransitionOutcome.DEFAULT, txn.firstValue.outcome)
    }

    @Test
    fun `approval to END records a terminal APPROVE edge`()
    {
        val spec = approvalStep("END")
        val step = stepInstance(0, spec)
        val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())

        whenever(stepRepository.findInstanceIdById(step.id)).thenReturn(instanceId)
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(runningInstance())
        whenever(stepRepository.findByIdForUpdate(step.id)).thenReturn(step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(assigneeFor(decider)))
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(approveDecision()))
        whenever(transitionRepository.existsByFromStepInstanceId(step.id)).thenReturn(false)
        whenever(definitionRepository.findById(definitionId)).thenReturn(definitionWith(spec))

        newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)

        val txn = argumentCaptor<WorkflowStepTransition>()
        verify(transitionRepository).save(txn.capture())
        assertEquals(step.id, txn.firstValue.fromStepInstanceId)
        assertEquals(0, txn.firstValue.fromStepIndex)
        assertNull(txn.firstValue.toStepIndex)
        assertEquals(WorkflowTransitionOutcome.APPROVE, txn.firstValue.outcome)
    }

    @Test
    fun `rejection records a terminal REJECT edge`()
    {
        val spec = approvalStep("END")
        val step = stepInstance(0, spec)
        val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())

        whenever(stepRepository.findInstanceIdById(step.id)).thenReturn(instanceId)
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(runningInstance())
        whenever(stepRepository.findByIdForUpdate(step.id)).thenReturn(step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(assigneeFor(decider)))
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step.id)).thenReturn(emptyList())
        whenever(transitionRepository.existsByFromStepInstanceId(step.id)).thenReturn(false)
        whenever(definitionRepository.findById(definitionId)).thenReturn(definitionWith(spec))

        newEngine().recordDecision(step.id, decider, Decision.REJECT, "no")

        val txn = argumentCaptor<WorkflowStepTransition>()
        verify(transitionRepository).save(txn.capture())
        assertEquals(step.id, txn.firstValue.fromStepInstanceId)
        assertNull(txn.firstValue.toStepIndex)
        assertEquals(WorkflowTransitionOutcome.REJECT, txn.firstValue.outcome)
    }

    @Test
    fun `approval advancing to a later step records the target index`()
    {
        val step0Spec = approvalStep("1")
        val step1Spec = approvalStep("END")
        val step0 = stepInstance(0, step0Spec)
        val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())

        whenever(stepRepository.findInstanceIdById(step0.id)).thenReturn(instanceId)
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(runningInstance(step0Spec, step1Spec))
        whenever(stepRepository.findByIdForUpdate(step0.id)).thenReturn(step0)
        whenever(assigneeRepository.findAllByStepInstanceId(step0.id)).thenReturn(listOf(assigneeFor(decider)))
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step0.id)).thenReturn(listOf(approveDecision()))
        whenever(transitionRepository.existsByFromStepInstanceId(step0.id)).thenReturn(false)
        whenever(assigneeResolver.resolveAll(any(), any())).thenReturn(emptyList())

        newEngine().recordDecision(step0.id, decider, Decision.APPROVE, null)

        val txn = argumentCaptor<WorkflowStepTransition>()
        verify(transitionRepository).save(txn.capture())
        assertEquals(step0.id, txn.firstValue.fromStepInstanceId)
        assertEquals(0, txn.firstValue.fromStepIndex)
        assertEquals(1, txn.firstValue.toStepIndex)
        assertEquals(WorkflowTransitionOutcome.APPROVE, txn.firstValue.outcome)
    }

    @Test
    fun `an already-recorded source step does not record a second edge`()
    {
        val spec = approvalStep("END")
        val step = stepInstance(0, spec)
        val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())

        whenever(stepRepository.findInstanceIdById(step.id)).thenReturn(instanceId)
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(runningInstance())
        whenever(stepRepository.findByIdForUpdate(step.id)).thenReturn(step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(assigneeFor(decider)))
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(approveDecision()))
        whenever(transitionRepository.existsByFromStepInstanceId(step.id)).thenReturn(true)
        whenever(definitionRepository.findById(definitionId)).thenReturn(definitionWith(spec))

        newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)

        verify(transitionRepository, never()).save(any())
    }

    @Test
    fun `frozen snapshot is a copy independent of later definition edits`()
    {
        val definition = definitionWith(approvalStep("END"))
        val originalStepsJson = definition.stepsJson
        whenever(definitionRepository.findAllActiveForTrigger(any(), any())).thenReturn(listOf(definition))
        whenever(applicabilityEvaluator.isApplicable(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(true)
        whenever(assigneeResolver.resolveAll(any(), any())).thenReturn(emptyList())
        whenever(transitionRepository.existsStartByInstanceId(any())).thenReturn(false)

        val instanceCaptor = argumentCaptor<WorkflowInstance>()
        newEngine().trigger(TriggerRequest(triggerEvent = "test.event", organizationId = UUID.randomUUID()))
        verify(instanceRepository).save(instanceCaptor.capture())
        val frozen = instanceCaptor.firstValue.definitionSnapshotJson

        definition.stepsJson = WorkflowSpecJson.encode(
            WorkflowSpec(steps = listOf(approvalStep("END"), approvalStep("END"))),
        )
        definition.version = 4

        assertEquals(originalStepsJson, frozen)
        assertTrue(frozen != definition.stepsJson)
    }
}
