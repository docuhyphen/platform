package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry
import com.docuhyphen.app.api.repository.workflow.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowTriggerEventRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.fields.ExchangeFieldQueryService
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Verifies that condition-predicate validation is enforced through the definition service's
 * create, update, and clone paths, using the same validator and predicate contract that the
 * runtime engine uses. This proves save-time rejection happens before persistence rather than
 * only inside the isolated validator.
 */
class WorkflowDefinitionServiceValidationTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val triggerRepository: WorkflowTriggerEventRepository = mock()
    private val authorizationContextFactory: AuthorizationContextFactory = mock()
    private val userRoleService: UserRoleService = mock()
    private val fieldQueryService: ExchangeFieldQueryService = mock()
    private val actionHandlerCatalog: WorkflowActionHandlerCatalog = mock()
    private val communicationRepository: com.docuhyphen.app.api.repository.communication.CommunicationRepository = mock()

    private val principalId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()

    private val service = WorkflowDefinitionService(
        definitionRepository = definitionRepository,
        instanceRepository = instanceRepository,
        stepRepository = mock(),
        transitionRepository = mock(),
        assigneeRepository = mock(),
        decisionRepository = mock(),
        triggerEventRepository = triggerRepository,
        userContactService = mock(),
        principalGroupRepository = mock(),
        appUserService = mock(),
        exchangeRepository = mock(),
        authorizationService = mock(),
        authorizationContextFactory = authorizationContextFactory,
        userRoleService = userRoleService,
        applicabilityEvaluator = WorkflowApplicabilityEvaluator(fieldQueryService, FieldTypeRegistry()),
        workflowSpecValidator = WorkflowSpecValidator(
            triggerRepository,
            ConditionPredicateService(),
            actionHandlerCatalog,
            communicationRepository,
        ),
        auditRecorder = mock(),
        subscriptionGuard = mock(),
    )

    @Test
    fun `create rejects a condition step with an incompatible predicate`()
    {
        setupCaller()

        assertThrows(WorkflowSpecValidationException::class.java) {
            service.createDefinition(createRequest(conditionSteps(INVALID_PREDICATE)))
        }
        verify(definitionRepository, org.mockito.kotlin.never()).save(any())
    }

    @Test
    fun `create persists a definition with a valid predicate`()
    {
        setupCaller()
        whenever(definitionRepository.save(any())).doAnswer { it.getArgument(0) }

        assertDoesNotThrow {
            service.createDefinition(createRequest(conditionSteps(VALID_PREDICATE)))
        }
        verify(definitionRepository).save(any())
    }

    @Test
    fun `update rejects a condition step with an incompatible predicate`()
    {
        setupCaller()
        val existing = WorkflowDefinition().apply {
            id = UUID.randomUUID()
            name = "Existing"
            triggerEvent = TRIGGER
            scope = WorkflowScope.PERSONAL
            createdByAppUserId = principalId
        }
        whenever(definitionRepository.findById(existing.id)).thenReturn(existing)
        whenever(instanceRepository.findActiveForDefinition(existing.id)).thenReturn(emptyList())

        assertThrows(WorkflowSpecValidationException::class.java) {
            service.updateDefinition(
                existing.id,
                UpdateWorkflowDefinitionRequest(stepsJson = conditionSteps(INVALID_PREDICATE)),
            )
        }
        verify(definitionRepository, org.mockito.kotlin.never()).update(any())
    }

    @Test
    fun `clone rejects a source whose predicate is incompatible with its trigger`()
    {
        setupCaller()
        val source = WorkflowDefinition().apply {
            id = UUID.randomUUID()
            name = "Template"
            triggerEvent = TRIGGER
            scope = WorkflowScope.APP
            isTemplate = true
            stepsJson = conditionSteps(INVALID_PREDICATE)
        }
        whenever(definitionRepository.findById(source.id)).thenReturn(source)

        assertThrows(WorkflowSpecValidationException::class.java) {
            service.cloneDefinition(source.id, null)
        }
        verify(definitionRepository, org.mockito.kotlin.never()).save(any())
    }

    private fun setupCaller()
    {
        whenever(authorizationContextFactory.currentPrincipal())
            .thenReturn(PrincipalRef(PrincipalKind.USER, principalId))
        whenever(authorizationContextFactory.currentContext())
            .thenReturn(AuthorizationContext(activeOrgId = orgId))
        whenever(userRoleService.isOrgAdminIn(principalId, orgId)).thenReturn(false)
        whenever(userRoleService.isAppAdmin(principalId)).thenReturn(true)
        whenever(triggerRepository.findByEventName(TRIGGER)).thenReturn(
            WorkflowTriggerEventRegistry().apply {
                eventName = TRIGGER
                subjectFieldsJson = """[{"name":"amount","type":"number"}]"""
            },
        )
    }

    private fun createRequest(stepsJson: String) = CreateWorkflowDefinitionRequest(
        name = "Def",
        triggerEvent = TRIGGER,
        stepsJson = stepsJson,
        scope = "PERSONAL",
    )

    private fun conditionSteps(expression: String): String = WorkflowSpecJson.encode(
        WorkflowSpec(
            steps = listOf(
                WorkflowStepSpec(
                    type = WorkflowStepType.CONDITION,
                    predicateExpression = expression,
                    onTrue = StepOutcomeSpec("END"),
                    onFalse = StepOutcomeSpec("END"),
                ),
            ),
        ),
    )

    private companion object
    {
        const val TRIGGER = "exchange.definition_test"
        const val VALID_PREDICATE = "\$subject.amount >= 10"
        const val INVALID_PREDICATE = "\$subject.amount contains '1'"
    }
}
