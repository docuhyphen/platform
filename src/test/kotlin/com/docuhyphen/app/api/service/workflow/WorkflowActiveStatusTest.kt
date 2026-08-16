package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepInstanceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.util.UUID

/**
 * Locks in the single active-status definition and proves the engine's cancellation guard treats
 * an escalated (still pending) instance as cancellable while leaving terminal instances untouched.
 */
class WorkflowActiveStatusTest
{
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()

    @Test
    fun `active set is exactly running and escalated`()
    {
        assertEquals(
            setOf(WorkflowInstanceStatus.RUNNING, WorkflowInstanceStatus.ESCALATED),
            WorkflowInstanceStatus.ACTIVE,
        )
    }

    @Test
    fun `isActive is true only for running and escalated`()
    {
        assertTrue(WorkflowInstanceStatus.RUNNING.isActive)
        assertTrue(WorkflowInstanceStatus.ESCALATED.isActive)
        assertFalse(WorkflowInstanceStatus.COMPLETED.isActive)
        assertFalse(WorkflowInstanceStatus.REJECTED.isActive)
        assertFalse(WorkflowInstanceStatus.CANCELLED.isActive)
    }

    @Test
    fun `cancel cancels an escalated instance and skips its pending step`()
    {
        val instance = instanceWith(WorkflowInstanceStatus.ESCALATED)
        val pendingStep = WorkflowStepInstance().apply {
            id = UUID.randomUUID()
            instanceId = instance.id
            status = WorkflowStepStatus.PENDING
        }
        whenever(instanceRepository.findByIdForUpdate(instance.id)).thenReturn(instance)
        whenever(stepRepository.findByInstance(instance.id)).thenReturn(listOf(pendingStep))
        whenever(stepRepository.findByIdForUpdate(pendingStep.id)).thenReturn(pendingStep)

        newEngine().cancel(instance.id, "Exchange rescinded")

        assertEquals(WorkflowInstanceStatus.CANCELLED, instance.status)
        assertEquals(WorkflowStepStatus.SKIPPED, pendingStep.status)
        verify(instanceRepository).update(instance)
        verify(stepRepository).update(pendingStep)
    }

    @Test
    fun `cancel is a no-op on a terminal instance`()
    {
        val instance = instanceWith(WorkflowInstanceStatus.COMPLETED)
        whenever(instanceRepository.findByIdForUpdate(instance.id)).thenReturn(instance)

        newEngine().cancel(instance.id, "Exchange rescinded")

        assertEquals(WorkflowInstanceStatus.COMPLETED, instance.status)
        verify(instanceRepository, never()).update(instance)
    }

    private fun instanceWith(status: WorkflowInstanceStatus) = WorkflowInstance().apply {
        id = UUID.randomUUID()
        definitionId = UUID.randomUUID()
        this.status = status
        createdAt = Timestamp.from(java.time.Instant.now())
    }

    private fun newEngine(): DefaultWorkflowEngineService = DefaultWorkflowEngineService().also { engine ->
        inject(engine, "instanceRepository", instanceRepository)
        inject(engine, "stepRepository", stepRepository)
    }

    private fun inject(engine: DefaultWorkflowEngineService, fieldName: String, value: Any)
    {
        DefaultWorkflowEngineService::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(engine, value)
        }
    }
}
