package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepTransitionRepository
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkflowCounterpartyClearanceTest
{
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val eventPublisher: DomainEventPublisher = mock()

    @Test
    fun `a counterparty parked at its own clearance step does not block completion`()
    {
        val resourceId = UUID.randomUUID()
        val ownOrgId = UUID.randomUUID()
        val otherOrgId = UUID.randomUUID()
        val own = instance(ownOrgId, resourceId)
        val other = instance(otherOrgId, resourceId)
        val ownWait = waitStep(own)
        val otherWait = waitStep(other)

        whenever(stepRepository.findAwaitingCounterpartyForSubject("EXCHANGE", resourceId))
            .thenReturn(listOf(ownWait), emptyList())
        whenever(instanceRepository.findByIdForUpdate(own.id)).thenReturn(own)
        whenever(stepRepository.findByIdForUpdate(ownWait.id)).thenReturn(ownWait)
        whenever(instanceRepository.findForSubjectExcludingOrg("EXCHANGE", resourceId, ownOrgId))
            .thenReturn(listOf(other))
        whenever(stepRepository.findCurrent(other.id, other.currentStepIndex)).thenReturn(otherWait)
        whenever(transitionRepository.existsByFromStepInstanceId(ownWait.id)).thenReturn(false)

        newEngine().unblockWaitingCounterpartySteps("EXCHANGE", resourceId)

        assertEquals(WorkflowStepStatus.COMPLETED, ownWait.status)
        assertEquals(WorkflowInstanceStatus.COMPLETED, own.status)
        verify(instanceRepository).update(own)
        verify(stepRepository).update(ownWait)
    }

    @Test
    fun `an active counterparty before its clearance step continues to block`()
    {
        val resourceId = UUID.randomUUID()
        val ownOrgId = UUID.randomUUID()
        val otherOrgId = UUID.randomUUID()
        val own = instance(ownOrgId, resourceId)
        val other = instance(otherOrgId, resourceId)
        val ownWait = waitStep(own)
        val otherApproval = WorkflowStepInstance().apply {
            instanceId = other.id
            stepIndex = 0
            stepType = WorkflowStepType.APPROVAL
            status = WorkflowStepStatus.PENDING
            specSnapshotJson = WorkflowSpecJson.encodeStep(
                WorkflowStepSpec(type = WorkflowStepType.APPROVAL, onApprove = StepOutcomeSpec("END")),
            )
        }

        whenever(stepRepository.findAwaitingCounterpartyForSubject("EXCHANGE", resourceId))
            .thenReturn(listOf(ownWait))
        whenever(instanceRepository.findByIdForUpdate(own.id)).thenReturn(own)
        whenever(stepRepository.findByIdForUpdate(ownWait.id)).thenReturn(ownWait)
        whenever(instanceRepository.findForSubjectExcludingOrg("EXCHANGE", resourceId, ownOrgId))
            .thenReturn(listOf(other))
        whenever(stepRepository.findCurrent(other.id, other.currentStepIndex)).thenReturn(otherApproval)

        newEngine().unblockWaitingCounterpartySteps("EXCHANGE", resourceId)

        assertEquals(WorkflowStepStatus.AWAITING_COUNTERPARTY, ownWait.status)
        assertEquals(WorkflowInstanceStatus.RUNNING, own.status)
        verify(instanceRepository, org.mockito.kotlin.never()).update(any())
    }

    private fun instance(organizationId: UUID, resourceId: UUID): WorkflowInstance =
        WorkflowInstance().apply {
            definitionId = UUID.randomUUID()
            definitionVersion = 1
            subjectResourceType = "EXCHANGE"
            subjectResourceId = resourceId
            this.organizationId = organizationId
            status = WorkflowInstanceStatus.RUNNING
            currentStepIndex = 0
            triggerEventSnapshot = "custom.event"
            definitionSnapshotJson = WorkflowSpecJson.encode(
                WorkflowSpec(steps = listOf(waitSpec())),
            )
        }

    private fun waitStep(instance: WorkflowInstance): WorkflowStepInstance =
        WorkflowStepInstance().apply {
            instanceId = instance.id
            stepIndex = 0
            stepType = WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE
            status = WorkflowStepStatus.AWAITING_COUNTERPARTY
            specSnapshotJson = WorkflowSpecJson.encodeStep(waitSpec())
        }

    private fun waitSpec(): WorkflowStepSpec = WorkflowStepSpec(
        type = WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE,
        onApprove = StepOutcomeSpec("END"),
    )

    private fun newEngine(): DefaultWorkflowEngineService = DefaultWorkflowEngineService().also { engine ->
        inject(engine, "instanceRepository", instanceRepository)
        inject(engine, "stepRepository", stepRepository)
        inject(engine, "transitionRepository", transitionRepository)
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
