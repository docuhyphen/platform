package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.Communication
import com.docuhyphen.app.api.model.entity.CommunicationScope
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry
import com.docuhyphen.app.api.repository.communication.CommunicationRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowTriggerEventRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Structural and referenced-resource validation for [WorkflowSpecValidator.validate]. Proves that a
 * definition which would fail open, stall, or auto-complete at runtime is rejected before it can be
 * saved, and that every rejection carries a stable code and field path.
 */
class WorkflowSpecValidatorStructureTest
{
    private val triggerRepository: WorkflowTriggerEventRepository = mock()
    private val communicationRepository: CommunicationRepository = mock()
    private val actionHandlerCatalog: WorkflowActionHandlerCatalog = mock()
    private val validator = WorkflowSpecValidator(
        triggerRepository,
        ConditionPredicateService(),
        actionHandlerCatalog,
        communicationRepository,
    )

    private val creatorId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()

    // ── structural ───────────────────────────────────────────────────────────────

    @Test
    fun `rejects an empty definition`()
    {
        val error = validateExpectingError(WorkflowSpec(steps = emptyList()))
        assertEquals("WORKFLOW_EMPTY_DEFINITION", error.code)
    }

    @Test
    fun `rejects a self-routing step`()
    {
        val spec = WorkflowSpec(steps = listOf(notification(onApprove = "0")))
        assertTrue(codes(spec).contains("WORKFLOW_ROUTE_SELF"))
    }

    @Test
    fun `rejects an unreachable step`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                notification(onApprove = "END"),
                notification(onApprove = "END"),
            ),
        )
        val error = validateExpectingError(spec)
        assertEquals("WORKFLOW_STEP_UNREACHABLE", error.code)
        assertEquals(1, error.stepIndex)
    }

    @Test
    fun `rejects a step with no path to end`()
    {
        // 0 -> 1 -> 0 loops with no terminal edge; both are flagged for having no path to END
        // (and the cycle is detected too).
        val spec = WorkflowSpec(
            steps = listOf(
                notification(onApprove = "1"),
                notification(onApprove = "0"),
            ),
        )
        val codes = codes(spec)
        assertTrue(codes.contains("WORKFLOW_STEP_NO_PATH_TO_END"))
        assertTrue(codes.contains("WORKFLOW_CYCLE_DETECTED"))
    }

    @Test
    fun `accepts a linear notification chain that reaches end`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                notification(onApprove = "1"),
                notification(onApprove = "END"),
            ),
        )
        assertDoesNotThrow { validator.validate(spec, TRIGGER, null) }
    }

    // ── approval + quorum ─────────────────────────────────────────────────────────

    @Test
    fun `rejects an approval step with no assignees`()
    {
        val spec = WorkflowSpec(steps = listOf(approval(assignees = emptyList())))
        assertTrue(codes(spec).contains("WORKFLOW_APPROVAL_NO_ASSIGNEES"))
    }

    @Test
    fun `rejects an N_OF_M quorum below one`()
    {
        val spec = WorkflowSpec(
            steps = listOf(approval(assignees = listOf(principal()), quorum = QuorumSpec.NOfM(0))),
        )
        assertTrue(codes(spec).contains("WORKFLOW_QUORUM_INVALID"))
    }

    @Test
    fun `rejects an N_OF_M quorum larger than the static assignee count`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                approval(assignees = listOf(principal(), principal()), quorum = QuorumSpec.NOfM(3)),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_QUORUM_EXCEEDS_ASSIGNEES"))
    }

    @Test
    fun `accepts a large N_OF_M quorum when assignees resolve dynamically`()
    {
        val group = AssigneeSpec.GroupRoleAssignees("\$subject.groupId", PrincipalGroupRoleName.MANAGER)
        val spec = WorkflowSpec(
            steps = listOf(approval(assignees = listOf(group), quorum = QuorumSpec.NOfM(9))),
        )
        assertDoesNotThrow { validator.validate(spec, TRIGGER, null) }
    }

    // ── escalation + SLA ────────────────────────────────────────────────────────

    @Test
    fun `rejects escalation without an SLA`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                approval(
                    assignees = listOf(principal()),
                    slaMinutes = null,
                    escalation = EscalationSpec(EscalationAction.AUTO_REJECT),
                ),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_ESCALATION_NO_SLA"))
    }

    @Test
    fun `rejects ESCALATE with no targets`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                approval(
                    assignees = listOf(principal()),
                    slaMinutes = 60,
                    escalation = EscalationSpec(EscalationAction.ESCALATE, escalateTo = emptyList()),
                ),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_ESCALATION_NO_TARGETS"))
    }

    @Test
    fun `rejects a non-positive SLA`()
    {
        val spec = WorkflowSpec(steps = listOf(approval(assignees = listOf(principal()), slaMinutes = 0)))
        assertTrue(codes(spec).contains("WORKFLOW_SLA_INVALID"))
    }

    @Test
    fun `rejects a non-positive reminder interval`()
    {
        val spec = WorkflowSpec(
            steps = listOf(
                approval(
                    assignees = listOf(principal()),
                    addons = listOf(
                        StepAddonSpec.ReminderIfNoDecision(afterMinutes = 0, recipientRef = principal()),
                    ),
                ),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_REMINDER_INTERVAL_INVALID"))
    }

    // ── assignee well-formedness ───────────────────────────────────────────────────

    @Test
    fun `rejects a malformed principal assignee`()
    {
        val spec = WorkflowSpec(
            steps = listOf(approval(assignees = listOf(AssigneeSpec.Principal(PrincipalKind.USER, "not-a-uuid")))),
        )
        assertTrue(codes(spec).contains("WORKFLOW_ASSIGNEE_MALFORMED"))
    }

    @Test
    fun `rejects a malformed organization role reference`()
    {
        val bad = AssigneeSpec.OrganizationRoleAssignees(OrganizationRoleName.ORG_ADMIN, "nonsense")
        val spec = WorkflowSpec(steps = listOf(approval(assignees = listOf(bad))))
        assertTrue(codes(spec).contains("WORKFLOW_ASSIGNEE_MALFORMED"))
    }

    // ── condition branches ────────────────────────────────────────────────────────

    @Test
    fun `rejects a condition step missing a branch`()
    {
        registerFields()
        val spec = WorkflowSpec(
            steps = listOf(
                WorkflowStepSpec(
                    type = WorkflowStepType.CONDITION,
                    predicateExpression = "\$subject.amount >= 10",
                    onTrue = StepOutcomeSpec("END"),
                    onFalse = null,
                ),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_CONDITION_MISSING_BRANCH"))
    }

    // ── action handlers ────────────────────────────────────────────────────────────

    @Test
    fun `rejects an action step with no handler`()
    {
        val spec = WorkflowSpec(steps = listOf(action(handlerKey = null)))
        assertTrue(codes(spec).contains("WORKFLOW_ACTION_NO_HANDLER"))
    }

    @Test
    fun `rejects an action step with an unregistered handler`()
    {
        whenever(actionHandlerCatalog.isRegistered(any())).thenReturn(false)
        val spec = WorkflowSpec(steps = listOf(action(handlerKey = "no.such.handler")))
        assertTrue(codes(spec).contains("WORKFLOW_ACTION_UNKNOWN_HANDLER"))
    }

    @Test
    fun `rejects a webhook action without endpoint and event type`()
    {
        whenever(actionHandlerCatalog.isRegistered(WebhookWorkflowActionHandler.KEY)).thenReturn(true)
        val spec = WorkflowSpec(
            steps = listOf(
                action(handlerKey = WebhookWorkflowActionHandler.KEY, webhookEndpointId = null, webhookEventType = null),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_WEBHOOK_CONFIG_MISSING"))
    }

    @Test
    fun `rejects a webhook action with a malformed endpoint reference`()
    {
        whenever(actionHandlerCatalog.isRegistered(WebhookWorkflowActionHandler.KEY)).thenReturn(true)
        val spec = WorkflowSpec(
            steps = listOf(
                action(
                    handlerKey = WebhookWorkflowActionHandler.KEY,
                    webhookEndpointId = "not-a-uuid",
                    webhookEventType = "exchange.activated",
                ),
            ),
        )
        assertTrue(codes(spec).contains("WORKFLOW_WEBHOOK_ENDPOINT_INVALID"))
    }

    // ── referenced communications ──────────────────────────────────────────────────

    @Test
    fun `rejects a notification referencing a missing communication`()
    {
        val id = UUID.randomUUID()
        whenever(communicationRepository.findById(id)).thenReturn(null)
        val spec = WorkflowSpec(steps = listOf(notification(onApprove = "END", communicationId = id.toString())))
        assertTrue(codes(spec).contains("WORKFLOW_COMMUNICATION_NOT_FOUND"))
    }

    @Test
    fun `rejects a notification referencing an inactive communication`()
    {
        val id = UUID.randomUUID()
        whenever(communicationRepository.findById(id)).thenReturn(communication(id, isActive = false))
        val spec = WorkflowSpec(steps = listOf(notification(onApprove = "END", communicationId = id.toString())))
        assertTrue(codes(spec).contains("WORKFLOW_COMMUNICATION_INACTIVE"))
    }

    @Test
    fun `rejects a notification referencing a communication outside the definition scope`()
    {
        val id = UUID.randomUUID()
        whenever(communicationRepository.findById(id)).thenReturn(
            communication(id).apply {
                scope = CommunicationScope.PERSONAL
                createdByAppUserId = UUID.randomUUID()
            },
        )
        val spec = WorkflowSpec(steps = listOf(notification(onApprove = "END", communicationId = id.toString())))
        val scope = WorkflowDefinitionScope(WorkflowScope.PERSONAL, null, creatorId)

        val error = assertThrows(WorkflowSpecValidationException::class.java) {
            validator.validate(spec, TRIGGER, scope)
        }.errors.single()
        assertEquals("WORKFLOW_COMMUNICATION_OUT_OF_SCOPE", error.code)
    }

    // ── accepts a fully valid multi-type definition ─────────────────────────────────

    @Test
    fun `accepts a valid approval condition notification action and wait definition`()
    {
        registerFields()
        whenever(actionHandlerCatalog.isRegistered("exchange.auto-accept")).thenReturn(true)
        val commId = UUID.randomUUID()
        whenever(communicationRepository.findById(commId)).thenReturn(
            communication(commId).apply {
                scope = CommunicationScope.PERSONAL
                createdByAppUserId = creatorId
            },
        )

        val spec = WorkflowSpec(
            steps = listOf(
                approval(assignees = listOf(principal()), onApprove = "1"),
                WorkflowStepSpec(
                    type = WorkflowStepType.CONDITION,
                    predicateExpression = "\$subject.amount >= 10",
                    onTrue = StepOutcomeSpec("2"),
                    onFalse = StepOutcomeSpec("END"),
                ),
                notification(onApprove = "3", communicationId = commId.toString()),
                action(handlerKey = "exchange.auto-accept", onApprove = "4"),
                WorkflowStepSpec(
                    type = WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE,
                    onApprove = StepOutcomeSpec("END"),
                ),
            ),
        )

        assertDoesNotThrow {
            validator.validate(spec, TRIGGER, WorkflowDefinitionScope(WorkflowScope.PERSONAL, orgId, creatorId))
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    private fun codes(spec: WorkflowSpec): Set<String> =
        assertThrows(WorkflowSpecValidationException::class.java) {
            validator.validate(spec, TRIGGER, WorkflowDefinitionScope(WorkflowScope.PERSONAL, orgId, creatorId))
        }.errors.map { it.code }.toSet()

    private fun validateExpectingError(spec: WorkflowSpec): WorkflowValidationError =
        assertThrows(WorkflowSpecValidationException::class.java) {
            validator.validate(spec, TRIGGER, null)
        }.errors.first()

    private fun registerFields()
    {
        whenever(triggerRepository.findByEventName(TRIGGER)).thenReturn(
            WorkflowTriggerEventRegistry().apply {
                eventName = TRIGGER
                subjectFieldsJson = """[{"name":"amount","type":"number"}]"""
            },
        )
    }

    private fun principal() = AssigneeSpec.Principal(PrincipalKind.USER, UUID.randomUUID().toString())

    private fun approval(
        assignees: List<AssigneeSpec>,
        quorum: QuorumSpec = QuorumSpec.Any,
        slaMinutes: Int? = null,
        escalation: EscalationSpec? = null,
        addons: List<StepAddonSpec> = emptyList(),
        onApprove: String = "END",
    ) = WorkflowStepSpec(
        type = WorkflowStepType.APPROVAL,
        assignees = assignees,
        quorum = quorum,
        slaMinutes = slaMinutes,
        escalation = escalation,
        addons = addons,
        onApprove = StepOutcomeSpec(onApprove),
        onReject = StepOutcomeSpec("END"),
    )

    private fun notification(onApprove: String, communicationId: String? = null) = WorkflowStepSpec(
        type = WorkflowStepType.NOTIFICATION,
        communicationId = communicationId,
        onApprove = StepOutcomeSpec(onApprove),
    )

    private fun action(
        handlerKey: String?,
        webhookEndpointId: String? = null,
        webhookEventType: String? = null,
        onApprove: String = "END",
    ) = WorkflowStepSpec(
        type = WorkflowStepType.ACTION,
        actionHandlerKey = handlerKey,
        webhookEndpointId = webhookEndpointId,
        webhookEventType = webhookEventType,
        onApprove = StepOutcomeSpec(onApprove),
    )

    private fun communication(id: UUID, isActive: Boolean = true) = Communication().apply {
        this.id = id
        name = "Comm"
        subject = "s"
        body = "b"
        this.isActive = isActive
        isDeleted = false
        scope = CommunicationScope.PLATFORM
        isTemplate = true
    }

    private companion object
    {
        const val TRIGGER = "exchange.structure_test"
    }
}
