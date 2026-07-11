package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepAssignee
import com.docuhyphen.app.api.model.entity.WorkflowStepDecision
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepTransition
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.entity.WorkflowTransitionOutcome
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepAssigneeRepository
import com.docuhyphen.app.api.repository.WorkflowStepDecisionRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Verifies the Phase 4 frozen-instance-graph contract: the engine freezes the raw definition
 * snapshot at instance start and records every traversed edge explicitly (START, approve,
 * reject, and forward branch), deduplicating per source step instance.
 *
 * The engine uses field injection, so collaborators are set reflectively with Mockito mocks.
 * These are pure-logic unit tests; they do not touch a database.
 */
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
            // The engine advances from the frozen execution snapshot, so an instance must carry one.
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

    // ── trigger: freezes snapshot and records the START edge ─────────────────────

    @Test
    fun `trigger freezes the definition snapshot and records the START edge`()
    {
        val definition = definitionWith(approvalStep("END"))
        whenever(definitionRepository.findAllActiveForTrigger(any(), any())).thenReturn(listOf(definition))
        whenever(applicabilityEvaluator.isApplicable(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)
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

    // ── approval to END: terminal APPROVE edge ───────────────────────────────────

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

    // ── rejection: terminal REJECT edge ──────────────────────────────────────────

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

    // ── forward branch: APPROVE edge carries the resolved target index ────────────

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

    // ── dedup: a source step instance never records twice ────────────────────────

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

    // ── snapshot immutability: editing the definition cannot change the frozen copy ─

    @Test
    fun `frozen snapshot is a copy independent of later definition edits`()
    {
        val definition = definitionWith(approvalStep("END"))
        val originalStepsJson = definition.stepsJson
        whenever(definitionRepository.findAllActiveForTrigger(any(), any())).thenReturn(listOf(definition))
        whenever(applicabilityEvaluator.isApplicable(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)
        whenever(assigneeResolver.resolveAll(any(), any())).thenReturn(emptyList())
        whenever(transitionRepository.existsStartByInstanceId(any())).thenReturn(false)

        val instanceCaptor = argumentCaptor<WorkflowInstance>()
        newEngine().trigger(TriggerRequest(triggerEvent = "test.event", organizationId = UUID.randomUUID()))
        verify(instanceRepository).save(instanceCaptor.capture())
        val frozen = instanceCaptor.firstValue.definitionSnapshotJson

        // Simulate a later definition edit / version bump.
        definition.stepsJson = WorkflowSpecJson.encode(
            WorkflowSpec(steps = listOf(approvalStep("END"), approvalStep("END"))),
        )
        definition.version = 4

        assertEquals(originalStepsJson, frozen)
        assertTrue(frozen != definition.stepsJson)
    }
}
