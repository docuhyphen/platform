package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepAssignee
import com.docuhyphen.app.api.model.entity.WorkflowStepDecision
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepAssigneeRepository
import com.docuhyphen.app.api.repository.WorkflowStepDecisionRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Verifies the engine's transaction-level concurrency control: decision recording, SLA escalation,
 * and cancellation take a pessimistic write lock on the parent instance and the current step in a
 * fixed order and re-read the locked state before acting, so a race between two of these paths
 * resolves deterministically to a single outcome. These are pure-logic unit tests: the mocked
 * repositories stand in for the row-locking reads (`findByIdForUpdate`) whose real serialization is
 * enforced by Postgres, so they assert the engine's decision-after-relock behavior rather than the
 * database's lock semantics.
 */
class WorkflowConcurrencyControlTest
{
    private val definitionRepository: com.docuhyphen.app.api.repository.WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val assigneeRepository: WorkflowStepAssigneeRepository = mock()
    private val decisionRepository: WorkflowStepDecisionRepository = mock()
    private val assigneeResolver: WorkflowAssigneeResolver = mock()
    private val eventPublisher: DomainEventPublisher = mock()

    private val instanceId = UUID.randomUUID()
    private val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())

    // ── decision idempotency: the race loser records no second side effect ────────────

    @Test
    fun `a decision on an already-resolved step returns the recorded outcome with no side effects`()
    {
        val inst = instance(WorkflowInstanceStatus.RUNNING)
        val step = stepInstance(status = WorkflowStepStatus.APPROVED)
        lockStubs(inst, step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(assigneeFor(decider)))

        val result = newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)

        assertEquals(WorkflowStepStatus.APPROVED, result.stepStatus)
        assertEquals(WorkflowInstanceStatus.RUNNING, result.instanceStatus)
        assertEquals(emptyList<String>(), result.emittedEvents)
        verify(transitionRepository, never()).save(any())
        verify(decisionRepository, never()).save(any())
        verify(decisionRepository, never()).update(any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `a decision on a cancelled instance returns idempotently with no side effects`()
    {
        val inst = instance(WorkflowInstanceStatus.CANCELLED)
        val step = stepInstance(status = WorkflowStepStatus.PENDING)
        lockStubs(inst, step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(assigneeFor(decider)))

        val result = newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)

        assertEquals(WorkflowInstanceStatus.CANCELLED, result.instanceStatus)
        verify(transitionRepository, never()).save(any())
        verify(decisionRepository, never()).save(any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `recordDecision locks the parent instance before the step`()
    {
        val inst = instance(WorkflowInstanceStatus.RUNNING, snapshot = snapshotOf(approvalToEnd()))
        val step = stepInstance(status = WorkflowStepStatus.PENDING)
        lockStubs(inst, step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id))
            .thenReturn(listOf(assigneeFor(decider)))
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step.id))
            .thenReturn(listOf(approveDecision()))
        whenever(transitionRepository.existsByFromStepInstanceId(step.id)).thenReturn(false)

        newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)

        val order = inOrder(instanceRepository, stepRepository)
        order.verify(instanceRepository).findByIdForUpdate(instanceId)
        order.verify(stepRepository).findByIdForUpdate(step.id)
    }

    @Test
    fun `a non-assignee cannot inspect an already-resolved step through a decision request`()
    {
        val inst = instance(WorkflowInstanceStatus.COMPLETED)
        val step = stepInstance(status = WorkflowStepStatus.APPROVED)
        lockStubs(inst, step)
        whenever(assigneeRepository.findAllByStepInstanceId(step.id)).thenReturn(emptyList())

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)
        }

        verify(decisionRepository, never()).save(any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `a repeated same-principal vote replaces the prior decision without advancing`()
    {
        val inst = instance(WorkflowInstanceStatus.RUNNING, snapshot = snapshotOf(approvalToEnd()))
        val step = stepInstance(status = WorkflowStepStatus.PENDING)
        lockStubs(inst, step)
        // Two assignees, so a single approval leaves the 2-of-2 quorum unmet and the step PENDING.
        whenever(assigneeRepository.findAllByStepInstanceId(step.id))
            .thenReturn(listOf(assigneeFor(decider), assigneeFor(PrincipalRef(PrincipalKind.USER, UUID.randomUUID()))))
        val prior = WorkflowStepDecision().apply {
            stepInstanceId = step.id
            principalKind = decider.kind
            principalId = decider.id
            decision = Decision.APPROVE.name
        }
        whenever(decisionRepository.findByStepAndPrincipal(step.id, decider.kind, decider.id)).thenReturn(prior)
        whenever(decisionRepository.findAllByStepInstanceId(step.id)).thenReturn(listOf(prior))

        val step1Spec = WorkflowStepSpec(
            type = WorkflowStepType.APPROVAL,
            quorum = QuorumSpec.NOfM(2),
            onApprove = StepOutcomeSpec("END"),
        )
        step.specSnapshotJson = WorkflowSpecJson.encodeStep(step1Spec)

        newEngine().recordDecision(step.id, decider, Decision.APPROVE, null)

        verify(decisionRepository).update(prior)
        verify(decisionRepository, never()).save(any())
        verify(transitionRepository, never()).save(any())
        assertEquals(WorkflowStepStatus.PENDING, step.status)
        assertEquals(WorkflowInstanceStatus.RUNNING, inst.status)
    }

    // ── escalation: the scheduler re-reads under the lock ─────────────────────────────

    @Test
    fun `escalateOverdue skips a step a concurrent decision already completed`()
    {
        val inst = instance(WorkflowInstanceStatus.RUNNING)
        val candidate = stepInstance(status = WorkflowStepStatus.APPROVED, duePast = true)
        whenever(stepRepository.findPendingDueBefore(any())).thenReturn(listOf(candidate))
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(inst)
        whenever(stepRepository.findByIdForUpdate(candidate.id)).thenReturn(candidate)

        val escalated = newEngine().escalateOverdue(now())

        assertEquals(0, escalated)
        verify(eventPublisher, never()).publish(any())
        verify(stepRepository, never()).update(any())
    }

    @Test
    fun `escalateOverdue skips a step whose instance is no longer active`()
    {
        val inst = instance(WorkflowInstanceStatus.CANCELLED)
        val candidate = stepInstance(status = WorkflowStepStatus.PENDING, duePast = true)
        whenever(stepRepository.findPendingDueBefore(any())).thenReturn(listOf(candidate))
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(inst)
        whenever(stepRepository.findByIdForUpdate(candidate.id)).thenReturn(candidate)

        val escalated = newEngine().escalateOverdue(now())

        assertEquals(0, escalated)
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `escalateOverdue reassigns and escalates a genuinely overdue active step`()
    {
        val inst = instance(WorkflowInstanceStatus.RUNNING)
        val spec = WorkflowStepSpec(
            type = WorkflowStepType.APPROVAL,
            slaMinutes = 60,
            escalation = EscalationSpec(
                afterSlaBreach = EscalationAction.ESCALATE,
                escalateTo = listOf(AssigneeSpec.Principal(PrincipalKind.USER, UUID.randomUUID().toString())),
            ),
            onApprove = StepOutcomeSpec("END"),
        )
        val candidate = stepInstance(status = WorkflowStepStatus.PENDING, duePast = true)
        candidate.specSnapshotJson = WorkflowSpecJson.encodeStep(spec)
        whenever(stepRepository.findPendingDueBefore(any())).thenReturn(listOf(candidate))
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(inst)
        whenever(stepRepository.findByIdForUpdate(candidate.id)).thenReturn(candidate)
        whenever(assigneeResolver.resolveAll(any(), any()))
            .thenReturn(listOf(PrincipalRef(PrincipalKind.USER, UUID.randomUUID())))

        val escalated = newEngine().escalateOverdue(now())

        assertEquals(1, escalated)
        assertEquals(WorkflowInstanceStatus.ESCALATED, inst.status)
        assertNotNull(candidate.escalatedAt)
        val events = argumentCaptor<DomainEvent>()
        verify(eventPublisher, atLeastOnce()).publish(events.capture())
        assertEquals("workflow.escalated", events.allValues.single().type)
        verify(stepRepository).update(candidate)
        verify(instanceRepository).update(inst)
    }

    // ── cancellation: re-reads each step under its lock ───────────────────────────────

    @Test
    fun `cancel skips a step that is no longer pending on re-read`()
    {
        val inst = instance(WorkflowInstanceStatus.RUNNING)
        val staleView = stepInstance(status = WorkflowStepStatus.PENDING)
        // The locked re-read observes the step already completed by a racing advancement.
        val lockedView = stepInstance(status = WorkflowStepStatus.COMPLETED).apply { id = staleView.id }
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(inst)
        whenever(stepRepository.findByInstance(instanceId)).thenReturn(listOf(staleView))
        whenever(stepRepository.findByIdForUpdate(staleView.id)).thenReturn(lockedView)

        newEngine().cancel(instanceId, "Exchange rescinded")

        assertEquals(WorkflowInstanceStatus.CANCELLED, inst.status)
        assertEquals(WorkflowStepStatus.COMPLETED, lockedView.status)
        verify(stepRepository, never()).update(any())
    }

    // ── harness ───────────────────────────────────────────────────────────────────────

    private fun lockStubs(inst: WorkflowInstance, step: WorkflowStepInstance)
    {
        whenever(stepRepository.findInstanceIdById(step.id)).thenReturn(instanceId)
        whenever(instanceRepository.findByIdForUpdate(instanceId)).thenReturn(inst)
        whenever(stepRepository.findByIdForUpdate(step.id)).thenReturn(step)
    }

    private fun approvalToEnd(): WorkflowStepSpec =
        WorkflowStepSpec(type = WorkflowStepType.APPROVAL, onApprove = StepOutcomeSpec("END"))

    private fun snapshotOf(vararg steps: WorkflowStepSpec): String =
        WorkflowSpecJson.encode(WorkflowSpec(steps = steps.toList()))

    private fun instance(
        status: WorkflowInstanceStatus,
        snapshot: String? = null,
    ): WorkflowInstance = WorkflowInstance().apply {
        id = instanceId
        definitionId = UUID.randomUUID()
        this.status = status
        currentStepIndex = 0
        triggerEventSnapshot = "custom.event"
        definitionSnapshotJson = snapshot
        createdAt = now()
    }

    private fun stepInstance(
        status: WorkflowStepStatus,
        duePast: Boolean = false,
    ): WorkflowStepInstance = WorkflowStepInstance().apply {
        id = UUID.randomUUID()
        instanceId = this@WorkflowConcurrencyControlTest.instanceId
        stepIndex = 0
        stepType = WorkflowStepType.APPROVAL
        this.status = status
        specSnapshotJson = WorkflowSpecJson.encodeStep(approvalToEnd())
        if (duePast) dueAt = Timestamp.from(Instant.now().minusSeconds(3600))
        createdAt = now()
    }

    private fun assigneeFor(ref: PrincipalRef): WorkflowStepAssignee =
        WorkflowStepAssignee().apply {
            stepInstanceId = UUID.randomUUID()
            principalKind = ref.kind
            principalId = ref.id
        }

    private fun approveDecision(): WorkflowStepDecision =
        WorkflowStepDecision().apply {
            principalKind = decider.kind
            principalId = decider.id
            decision = Decision.APPROVE.name
        }

    private fun now(): Timestamp = Timestamp.from(Instant.now())

    private fun newEngine(): DefaultWorkflowEngineService = DefaultWorkflowEngineService().also { engine ->
        inject(engine, "definitionRepository", definitionRepository)
        inject(engine, "instanceRepository", instanceRepository)
        inject(engine, "stepRepository", stepRepository)
        inject(engine, "transitionRepository", transitionRepository)
        inject(engine, "assigneeRepository", assigneeRepository)
        inject(engine, "decisionRepository", decisionRepository)
        inject(engine, "assigneeResolver", assigneeResolver)
        inject(engine, "eventPublisher", eventPublisher)
    }

    private fun inject(target: Any, field: String, value: Any)
    {
        DefaultWorkflowEngineService::class.java.getDeclaredField(field).apply {
            isAccessible = true
            set(target, value)
        }
    }
}
