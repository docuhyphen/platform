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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Verifies that the runtime engine fails an instance through the controlled failure path when a
 * route target cannot resolve to a real step, or when an N_OF_M quorum can never be met by the
 * resolved assignees, instead of auto-completing and firing a lifecycle event.
 */
class WorkflowRuntimeFailureTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val assigneeRepository: WorkflowStepAssigneeRepository = mock()
    private val decisionRepository: WorkflowStepDecisionRepository = mock()
    private val assigneeResolver: WorkflowAssigneeResolver = mock()
    private val eventPublisher: DomainEventPublisher = mock()

    private val instanceId = UUID.randomUUID()

    @Test
    fun `an out-of-range route target fails the instance without firing a lifecycle event`()
    {
        val inst = failAfterApprovingFirstStep(approvalStep("5"), triggerEvent = "exchange.acceptance_pending")

        assertEquals(WorkflowInstanceStatus.FAILED, inst.status)
        assertEquals("ROUTE_INVALID", inst.failureCode)
        val types = publishedEventTypes()
        assertTrue(types.contains("workflow.failed"))
        assertFalse(types.contains("exchange.activated"))
    }

    @Test
    fun `a non-numeric route target fails the instance`()
    {
        val inst = failAfterApprovingFirstStep(approvalStep("banana"))

        assertEquals(WorkflowInstanceStatus.FAILED, inst.status)
        assertEquals("ROUTE_INVALID", inst.failureCode)
    }

    @Test
    fun `an impossible N_OF_M quorum on the next step fails the instance`()
    {
        val step0 = approvalStep("1")
        val step1 = WorkflowStepSpec(
            type = WorkflowStepType.APPROVAL,
            quorum = QuorumSpec.NOfM(2),
            onApprove = StepOutcomeSpec("END"),
        )
        val inst = failAfterApprovingFirstStep(step0, step1)

        assertEquals(WorkflowInstanceStatus.FAILED, inst.status)
        assertEquals("QUORUM_UNSATISFIABLE", inst.failureCode)
        assertTrue(publishedEventTypes().contains("workflow.failed"))
    }

    // ── harness ────────────────────────────────────────────────────────────────────

    private fun failAfterApprovingFirstStep(
        vararg steps: WorkflowStepSpec,
        triggerEvent: String = "custom.event",
    ): WorkflowInstance
    {
        val inst = WorkflowInstance().apply {
            id = instanceId
            definitionId = UUID.randomUUID()
            definitionVersion = 1
            status = WorkflowInstanceStatus.RUNNING
            currentStepIndex = 0
            triggerEventSnapshot = triggerEvent
            definitionSnapshotJson = WorkflowSpecJson.encode(WorkflowSpec(steps = steps.toList()))
        }
        val step0 = WorkflowStepInstance().apply {
            id = UUID.randomUUID()
            instanceId = this@WorkflowRuntimeFailureTest.instanceId
            stepIndex = 0
            stepType = steps[0].type
            status = WorkflowStepStatus.PENDING
            specSnapshotJson = WorkflowSpecJson.encodeStep(steps[0])
        }

        val decider = PrincipalRef(PrincipalKind.USER, UUID.randomUUID())
        whenever(stepRepository.findInstanceIdById(step0.id)).thenReturn(inst.id)
        whenever(instanceRepository.findByIdForUpdate(inst.id)).thenReturn(inst)
        whenever(stepRepository.findByIdForUpdate(step0.id)).thenReturn(step0)
        whenever(assigneeRepository.findAllByStepInstanceId(step0.id)).thenReturn(
            listOf(WorkflowStepAssignee().apply { principalKind = decider.kind; principalId = decider.id }),
        )
        whenever(decisionRepository.findByStepAndPrincipal(any(), any(), any())).thenReturn(null)
        whenever(decisionRepository.findAllByStepInstanceId(step0.id)).thenReturn(
            listOf(WorkflowStepDecision().apply { decision = Decision.APPROVE.name }),
        )
        whenever(transitionRepository.existsByFromStepInstanceId(any())).thenReturn(false)
        whenever(assigneeResolver.resolveAll(any(), any())).thenReturn(emptyList())

        newEngine().recordDecision(step0.id, decider, Decision.APPROVE, null)
        return inst
    }

    private fun approvalStep(nextStep: String): WorkflowStepSpec =
        WorkflowStepSpec(type = WorkflowStepType.APPROVAL, onApprove = StepOutcomeSpec(nextStep))

    private fun publishedEventTypes(): List<String>
    {
        val captor = argumentCaptor<DomainEvent>()
        verify(eventPublisher, atLeastOnce()).publish(captor.capture())
        return captor.allValues.map { it.type }
    }

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
}
