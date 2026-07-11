package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepAssignee
import com.docuhyphen.app.api.model.entity.WorkflowStepDecision
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.entity.WorkflowTransitionOutcome
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepAssigneeRepository
import com.docuhyphen.app.api.repository.WorkflowStepDecisionRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Verifies that the engine executes an in-flight instance from its frozen definition snapshot and
 * frozen trigger, independently of later live-definition edits, and that a missing or corrupt
 * snapshot fails the instance through the controlled failure path without touching the Exchange.
 *
 * The engine uses field injection, so collaborators are set reflectively with Mockito mocks. These
 * are pure-logic unit tests; they do not touch a database.
 */
class WorkflowFrozenSnapshotExecutionTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val assigneeRepository: WorkflowStepAssigneeRepository = mock()
    private val decisionRepository: WorkflowStepDecisionRepository = mock()
    private val assigneeResolver: WorkflowAssigneeResolver = mock()
    private val eventPublisher: DomainEventPublisher = mock()

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

    private fun instance(
        snapshotJson: String?,
        triggerEvent: String? = "test.event",
    ): WorkflowInstance =
        WorkflowInstance().apply {
            id = instanceId
            definitionId = this@WorkflowFrozenSnapshotExecutionTest.definitionId
            definitionVersion = 3
            status = WorkflowInstanceStatus.RUNNING
            currentStepIndex = 0
            triggerEventSnapshot = triggerEvent
            definitionSnapshotJson = snapshotJson
        }

    private fun snapshotOf(vararg steps: WorkflowStepSpec, onComplete: String? = null): String =
        WorkflowSpecJson.encode(WorkflowSpec(steps = steps.toList(), onComplete = onComplete))

    private fun stepInstance(index: Int, spec: WorkflowStepSpec): WorkflowStepInstance =
        WorkflowStepInstance().apply {
            id = UUID.randomUUID()
            instanceId = this@WorkflowFrozenSnapshotExecutionTest.instanceId
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

    private fun approveFirstStep(inst: WorkflowInstance, step0: WorkflowStepInstance): DecisionResult
    {
        val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())
        whenever(stepRepository.findInstanceIdById(step0.id)).thenReturn(inst.id)
        whenever(instanceRepository.findByIdForUpdate(inst.id)).thenReturn(inst)
        whenever(stepRepository.findByIdForUpdate(step0.id)).thenReturn(step0)
        whenever(assigneeRepository.findAllByStepInstanceId(step0.id)).thenReturn(listOf(assigneeFor(decider)))
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step0.id)).thenReturn(listOf(approveDecision()))
        whenever(transitionRepository.existsByFromStepInstanceId(step0.id)).thenReturn(false)
        whenever(assigneeResolver.resolveAll(any(), any())).thenReturn(emptyList())
        return newEngine().recordDecision(step0.id, decider, Decision.APPROVE, null)
    }

    private fun publishedEventTypes(): List<String>
    {
        val captor = argumentCaptor<DomainEvent>()
        verify(eventPublisher, atLeastOnce()).publish(captor.capture())
        return captor.allValues.map { it.type }
    }

    // ── frozen topology drives advancement, not the live definition ──────────────

    @Test
    fun `advancement follows the frozen snapshot even when the live definition changed`()
    {
        val step0Spec = approvalStep("1")
        val step1Spec = approvalStep("END")
        val inst = instance(snapshotOf(step0Spec, step1Spec))
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        // A new step instance for the frozen index 1 is materialised, and the edge targets it.
        val savedStep = argumentCaptor<WorkflowStepInstance>()
        verify(stepRepository).save(savedStep.capture())
        assertEquals(1, savedStep.firstValue.stepIndex)
        assertEquals(1, inst.currentStepIndex)

        val edge = argumentCaptor<com.docuhyphen.app.api.model.entity.WorkflowStepTransition>()
        verify(transitionRepository).save(edge.capture())
        assertEquals(1, edge.firstValue.toStepIndex)
        assertEquals(WorkflowTransitionOutcome.APPROVE, edge.firstValue.outcome)
    }

    // ── frozen explicit terminal event honored ───────────────────────────────────

    @Test
    fun `terminal onComplete comes from the frozen snapshot`()
    {
        val step0Spec = approvalStep("END")
        val inst = instance(snapshotOf(step0Spec, onComplete = "exchange.activated"))
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        assertEquals(WorkflowInstanceStatus.COMPLETED, inst.status)
        assertTrue(publishedEventTypes().contains("exchange.activated"))
    }

    // ── frozen trigger drives the default terminal event ─────────────────────────

    @Test
    fun `terminal fallback uses the frozen trigger, not the live definition`()
    {
        val step0Spec = approvalStep("END")
        // No explicit onComplete: the frozen trigger must yield the canonical lifecycle default.
        val inst = instance(snapshotOf(step0Spec), triggerEvent = "exchange.acceptance_pending")
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        assertEquals(WorkflowInstanceStatus.COMPLETED, inst.status)
        assertTrue(publishedEventTypes().contains("exchange.activated"))
    }

    // ── missing snapshot fails the instance without touching the Exchange ────────

    @Test
    fun `a missing snapshot marks the instance FAILED and fires no lifecycle event`()
    {
        val step0Spec = approvalStep("1")
        val inst = instance(snapshotJson = null)
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        assertEquals(WorkflowInstanceStatus.FAILED, inst.status)
        assertEquals("SNAPSHOT_MISSING", inst.failureCode)
        assertNotNull(inst.completedAt)

        val edge = argumentCaptor<com.docuhyphen.app.api.model.entity.WorkflowStepTransition>()
        verify(transitionRepository).save(edge.capture())
        assertEquals(WorkflowTransitionOutcome.FAILED, edge.firstValue.outcome)
        assertNull(edge.firstValue.toStepIndex)

        val types = publishedEventTypes()
        assertTrue(types.contains("workflow.failed"))
        assertFalse(types.contains("exchange.activated"))
    }

    // ── corrupt snapshot fails the instance ──────────────────────────────────────

    @Test
    fun `a corrupt snapshot marks the instance FAILED with SNAPSHOT_CORRUPT`()
    {
        val step0Spec = approvalStep("1")
        val inst = instance(snapshotJson = "{ this is not valid workflow json ")
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        assertEquals(WorkflowInstanceStatus.FAILED, inst.status)
        assertEquals("SNAPSHOT_CORRUPT", inst.failureCode)
        assertTrue(publishedEventTypes().contains("workflow.failed"))
    }

    // ── failed instance never fabricates a successful terminal edge ──────────────

    @Test
    fun `a failed instance records only a FAILED terminal edge`()
    {
        val step0Spec = approvalStep("1")
        val inst = instance(snapshotJson = null)
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        val edge = argumentCaptor<com.docuhyphen.app.api.model.entity.WorkflowStepTransition>()
        verify(transitionRepository).save(edge.capture())
        assertEquals(1, edge.allValues.size)
        assertEquals(WorkflowTransitionOutcome.FAILED, edge.firstValue.outcome)
    }

    // ── default terminal fallback still works when the frozen spec sets no terminal event ─

    @Test
    fun `a non-lifecycle frozen trigger completes without emitting a terminal event`()
    {
        val step0Spec = approvalStep("END")
        val inst = instance(snapshotOf(step0Spec), triggerEvent = "custom.event")
        val step0 = stepInstance(0, step0Spec)

        approveFirstStep(inst, step0)

        assertEquals(WorkflowInstanceStatus.COMPLETED, inst.status)
        // No lifecycle default for a custom trigger and no explicit onComplete: nothing is published.
        verify(eventPublisher, never()).publish(any())
    }
}
