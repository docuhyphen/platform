package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.exception.WorkflowStartRefusedException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.model.workflow.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.repository.workflow.*
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.subscription.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(
            listOf(
                waitDefinition()
            )
        )
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)

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
        whenever(
            definitionRepository.findAllActiveForTrigger(
                eq(SUBMIT),
                eq(organizationId)
            )
        ).thenReturn(listOf(allowed, refused))
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)
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
    fun `a matching definition whose steps cannot be read refuses the start before any instance is created`() {
        val readable = waitDefinition()
        val unreadable = waitDefinition().apply { stepsJson = "{\"steps\": [{\"type\": " }
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(
            listOf(
                readable,
                unreadable
            )
        )
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)

        val refusal = assertThrows<WorkflowStartRefusedException> { newEngine().trigger(submitTrigger()) }

        assertEquals(unreadable.id, refusal.definitionId)
        assertEquals(WorkflowStartRefusedException.SPEC_UNREADABLE, refusal.reasonCode)
        verify(instanceRepository, never()).save(any())
    }

    @Test
    fun `a first step naming a malformed principal or a definition without its owner refuses the start`() {
        val malformed = waitDefinition().apply {
            stepsJson = WorkflowSpecJson.encode(
                WorkflowSpec(
                    steps = listOf(
                        WorkflowStepSpec(
                            type = WorkflowStepType.APPROVAL,
                            assignees = listOf(AssigneeSpec.Principal(PrincipalKind.USER, "not-an-identifier")),
                            onApprove = StepOutcomeSpec("END"),
                            onReject = StepOutcomeSpec("END"),
                        ),
                    ),
                ),
            )
        }
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(
            listOf(
                malformed
            )
        )
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)

        val assignee = assertThrows<WorkflowStartRefusedException> { newEngine().trigger(submitTrigger()) }
        assertEquals(malformed.id, assignee.definitionId)
        assertEquals(WorkflowStartRefusedException.ASSIGNEE_INVALID, assignee.reasonCode)

        val ownerless = waitDefinition().apply { this.organizationId = null }
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(
            listOf(
                ownerless
            )
        )
        doThrow(IllegalArgumentException("Organization workflow has no subscription owner"))
            .whenever(subscriptionGuard).requireInstanceStart(ownerless, organizationId)

        val owner = assertThrows<WorkflowStartRefusedException> { newEngine().trigger(submitTrigger()) }
        assertEquals(ownerless.id, owner.definitionId)
        assertEquals(WorkflowStartRefusedException.OWNER_UNRESOLVED, owner.reasonCode)
        verify(instanceRepository, never()).save(any())
    }

    @Test
    fun `a personally owned request trigger starts its owner's personal definitions instead of the platform ones`() {
        val ownerId = UUID.randomUUID()
        val personal = waitDefinition().apply {
            scope = WorkflowScope.PERSONAL
            this.organizationId = null
            createdByAppUserId = ownerId
        }
        whenever(definitionRepository.findAllActiveForPersonalTrigger(SUBMIT, ownerId)).thenReturn(listOf(personal))
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)

        val started = newEngine().trigger(
            TriggerRequest(
                triggerEvent = SUBMIT,
                subjectResourceType = "INFORMATION_REQUEST",
                subjectResourceId = requestId,
                ownerAppUserId = ownerId,
            ),
        )

        assertEquals(personal.id, started?.definitionId)
        verify(subscriptionGuard).requireInstanceStart(personal, null)
        verify(definitionRepository, never()).findAllActiveForTrigger(any(), anyOrNull())
    }

    @Test
    fun `a personally owned workflow records its events under its owner rather than the platform`() {
        val ownerId = UUID.randomUUID()
        val personal = approvalDefinition(ownerId).apply {
            scope = WorkflowScope.PERSONAL
            this.organizationId = null
            createdByAppUserId = ownerId
        }
        whenever(definitionRepository.findAllActiveForPersonalTrigger(SUBMIT, ownerId)).thenReturn(listOf(personal))
        whenever(definitionRepository.findById(personal.id)).thenReturn(personal)
        whenever(assigneeResolver.resolveAll(any(), anyOrNull())).thenReturn(listOf(PrincipalRef.user(ownerId)))
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)

        newEngine().trigger(
            TriggerRequest(
                triggerEvent = SUBMIT,
                subjectResourceType = "INFORMATION_REQUEST",
                subjectResourceId = requestId,
                ownerAppUserId = ownerId,
            ),
        )

        verify(eventPublisher).publish(
            argThat<DomainEvent> { type == "workflow.step_assigned" },
            eq(AuditOwnerScope.Personal(ownerId)),
        )
    }

    @Test
    fun `an organization workflow records its events under the organization`() {
        val approverId = UUID.randomUUID()
        val organizationDefinition = approvalDefinition(approverId)
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId)))
            .thenReturn(listOf(organizationDefinition))
        whenever(definitionRepository.findById(organizationDefinition.id)).thenReturn(organizationDefinition)
        whenever(assigneeResolver.resolveAll(any(), anyOrNull())).thenReturn(listOf(PrincipalRef.user(approverId)))
        whenever(
            applicabilityEvaluator.isApplicable(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(true)

        newEngine().trigger(submitTrigger())

        verify(eventPublisher).publish(
            argThat<DomainEvent> { type == "workflow.step_assigned" },
            eq(AuditOwnerScope.Organization(organizationId)),
        )
    }

    @Test
    fun `the decisions recorded on an instance are reported oldest first with who made them`() {
        val instanceId = UUID.randomUUID()
        val approverId = UUID.randomUUID()
        val rejecterId = UUID.randomUUID()
        val approvedAt = Instant.now().minusSeconds(120)
        val rejectedAt = Instant.now()
        val firstStep = step(instanceId, 0)
        val secondStep = step(instanceId, 1)
        whenever(stepRepository.findByInstance(instanceId)).thenReturn(listOf(secondStep, firstStep))
        whenever(decisionRepository.findAllByStepInstanceId(firstStep.id))
            .thenReturn(listOf(decision(firstStep, approverId, "APPROVE", approvedAt)))
        whenever(decisionRepository.findAllByStepInstanceId(secondStep.id))
            .thenReturn(listOf(decision(secondStep, rejecterId, "REJECT", rejectedAt)))

        val decisions = newEngine().decisionsOf(instanceId)

        assertEquals(
            listOf(
                WorkflowDecisionRecord(PrincipalRef.user(approverId), Decision.APPROVE, approvedAt),
                WorkflowDecisionRecord(PrincipalRef.user(rejecterId), Decision.REJECT, rejectedAt),
            ),
            decisions,
        )
    }

    @Test
    fun `an organization request trigger never consults personal definitions`() {
        whenever(definitionRepository.findAllActiveForTrigger(eq(SUBMIT), eq(organizationId))).thenReturn(emptyList())

        newEngine().trigger(submitTrigger().copy(ownerAppUserId = UUID.randomUUID()))

        verify(definitionRepository, never()).findAllActiveForPersonalTrigger(any(), any())
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

    private fun step(instanceId: UUID, index: Int) = WorkflowStepInstance().apply {
        this.instanceId = instanceId
        stepIndex = index
        stepType = WorkflowStepType.APPROVAL
        status = WorkflowStepStatus.APPROVED
        createdAt = Timestamp.from(Instant.now())
    }

    private fun decision(step: WorkflowStepInstance, principalId: UUID, outcome: String, at: Instant) =
        WorkflowStepDecision().apply {
            stepInstanceId = step.id
            principalKind = PrincipalKind.USER
            this.principalId = principalId
            decision = outcome
            decidedAt = Timestamp.from(at)
        }

    private fun approvalDefinition(approverId: UUID): WorkflowDefinition = WorkflowDefinition().apply {
        name = "Approval on submission"
        scope = WorkflowScope.ORG
        this.organizationId = this@WorkflowInformationRequestSubjectTest.organizationId
        triggerEvent = SUBMIT
        stepsJson = WorkflowSpecJson.encode(
            WorkflowSpec(
                steps = listOf(
                    WorkflowStepSpec(
                        type = WorkflowStepType.APPROVAL,
                        assignees = listOf(AssigneeSpec.Principal(PrincipalKind.USER, approverId.toString())),
                        onApprove = StepOutcomeSpec("END"),
                        onReject = StepOutcomeSpec("END"),
                    ),
                ),
            ),
        )
    }

    private fun submitTrigger() = TriggerRequest(
        triggerEvent = SUBMIT,
        subjectResourceType = "INFORMATION_REQUEST",
        subjectResourceId = requestId,
        organizationId = organizationId,
    )

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
