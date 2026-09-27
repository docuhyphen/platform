package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepAssigneeRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepDecisionRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepTransitionRepository
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class WorkflowInformationRequestSubjectTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val stepRepository: WorkflowStepInstanceRepository = mock()
    private val transitionRepository: WorkflowStepTransitionRepository = mock()
    private val assigneeRepository: WorkflowStepAssigneeRepository = mock()
    private val decisionRepository: WorkflowStepDecisionRepository = mock()
    private val assigneeResolver: WorkflowAssigneeResolver = mock()
    private val applicabilityEvaluator: WorkflowApplicabilityEvaluator = mock()
    private val subscriptionGuard: WorkflowSubscriptionGuard = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val principalGroupMemberRepository: PrincipalGroupMemberRepository = mock()
    private val exchangeRepository: ExchangeRepository = mock()
    private val appUserRepository: AppUserRepository = mock()
    private val requestId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    @Test
    fun `a counterparty wait on a request subject completes at once because it only observes other organizations`()
    {
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(listOf(waitDefinition()))
        whenever(applicabilityEvaluator.isApplicable(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(true)

        newEngine().trigger(
            TriggerRequest(
                triggerEvent = SUBMIT,
                subjectResourceType = "INFORMATION_REQUEST",
                subjectResourceId = requestId,
                organizationId = organizationId,
                subjectData = mapOf("requestId" to requestId.toString()),
            ),
        )

        val instances = argumentCaptor<WorkflowInstance>()
        verify(instanceRepository).save(instances.capture())
        val steps = argumentCaptor<WorkflowStepInstance>()
        verify(stepRepository).save(steps.capture())
        assertEquals(WorkflowInstanceStatus.COMPLETED, instances.firstValue.status)
        assertEquals(WorkflowStepStatus.COMPLETED, steps.firstValue.status)
        verify(instanceRepository).findForSubjectExcludingOrg("INFORMATION_REQUEST", requestId, organizationId)
    }

    @Test
    fun `a refusal for any matching definition starts no instance for any of them`()
    {
        val allowed = waitDefinition()
        val refused = waitDefinition()
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(listOf(allowed, refused))
        whenever(applicabilityEvaluator.isApplicable(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn(true)
        doThrow(denial()).whenever(subscriptionGuard).requireInstanceStart(refused, organizationId)

        assertThrows<SubscriptionDenialException> {
            newEngine().trigger(
                TriggerRequest(
                    triggerEvent = SUBMIT,
                    subjectResourceType = "INFORMATION_REQUEST",
                    subjectResourceId = requestId,
                    organizationId = organizationId,
                ),
            )
        }

        verify(instanceRepository, never()).save(any())
    }

    @Test
    fun `a pending approval started by a request trigger links the request's Exchange`()
    {
        val userId = UUID.randomUUID()
        val exchangeId = UUID.randomUUID()
        val instance = WorkflowInstance().apply {
            definitionId = UUID.randomUUID()
            definitionVersion = 1
            subjectResourceType = "INFORMATION_REQUEST"
            subjectResourceId = requestId
            organizationId = this@WorkflowInformationRequestSubjectTest.organizationId
            status = WorkflowInstanceStatus.RUNNING
            subjectDataJson = """{"requestId":"$requestId","exchangeId":"$exchangeId"}"""
        }
        val step = WorkflowStepInstance().apply {
            instanceId = instance.id
            stepIndex = 0
            stepType = WorkflowStepType.APPROVAL
            status = WorkflowStepStatus.PENDING
            createdAt = Timestamp.from(Instant.now())
        }
        whenever(stepRepository.findPendingForAssignee(eq(userId), any())).thenReturn(listOf(step))
        whenever(instanceRepository.findById(instance.id)).thenReturn(instance)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(Exchange().apply { name = "Collection process" })

        val pending = newEngine().listPendingForUser(userId).single()

        assertEquals(exchangeId.toString(), pending.exchangeId)
        assertEquals("Collection process", pending.name)
    }

    private fun waitDefinition(): WorkflowDefinition = WorkflowDefinition().apply {
        name = "Clearance on submission"
        scope = WorkflowScope.ORG
        this.organizationId = this@WorkflowInformationRequestSubjectTest.organizationId
        triggerEvent = SUBMIT
        stepsJson = WorkflowSpecJson.encode(
            WorkflowSpec(
                steps = listOf(
                    WorkflowStepSpec(
                        type = WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE,
                        onApprove = StepOutcomeSpec("END"),
                    ),
                ),
            ),
        )
    }

    private fun denial() = SubscriptionDenialException(
        SubscriptionDenial(
            reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
            planCode = PlanCode.PERSONAL,
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            feature = PlanFeature.WORKFLOW_AUTOMATION,
            message = "Workflow automation is not included",
        ),
    )

    private fun newEngine(): DefaultWorkflowEngineService = DefaultWorkflowEngineService().also { engine ->
        inject(engine, "definitionRepository", definitionRepository)
        inject(engine, "instanceRepository", instanceRepository)
        inject(engine, "stepRepository", stepRepository)
        inject(engine, "transitionRepository", transitionRepository)
        inject(engine, "assigneeRepository", assigneeRepository)
        inject(engine, "decisionRepository", decisionRepository)
        inject(engine, "assigneeResolver", assigneeResolver)
        inject(engine, "applicabilityEvaluator", applicabilityEvaluator)
        inject(engine, "subscriptionGuard", subscriptionGuard)
        inject(engine, "eventPublisher", eventPublisher)
        inject(engine, "principalGroupMemberRepository", principalGroupMemberRepository)
        inject(engine, "exchangeRepository", exchangeRepository)
        inject(engine, "appUserRepository", appUserRepository)
    }

    private fun inject(target: Any, field: String, value: Any)
    {
        DefaultWorkflowEngineService::class.java.getDeclaredField(field).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private companion object
    {
        const val SUBMIT = "information_request.request.submit"
    }
}
