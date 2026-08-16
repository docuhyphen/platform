package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.repository.workflow.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.UserRoleService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Proves an escalated (still pending) instance blocks definition edits and deletes the same way a
 * running instance does, so an SLA breach cannot open a window to mutate an in-flight definition.
 */
class WorkflowDefinitionServiceActiveBlockingTest
{
    private val definitionRepository: WorkflowDefinitionRepository = mock()
    private val instanceRepository: WorkflowInstanceRepository = mock()
    private val authorizationContextFactory: AuthorizationContextFactory = mock()
    private val userRoleService: UserRoleService = mock()

    private val principalId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()

    private val service = WorkflowDefinitionService(
        definitionRepository = definitionRepository,
        instanceRepository = instanceRepository,
        stepRepository = mock(),
        transitionRepository = mock(),
        assigneeRepository = mock(),
        decisionRepository = mock(),
        triggerEventRepository = mock(),
        userContactService = mock(),
        principalGroupRepository = mock(),
        appUserService = mock(),
        exchangeRepository = mock(),
        authorizationService = mock(),
        authorizationContextFactory = authorizationContextFactory,
        userRoleService = userRoleService,
        applicabilityEvaluator = mock(),
        workflowSpecValidator = mock(),
        auditRecorder = mock(),
        subscriptionGuard = mock(),
    )

    @Test
    fun `update is blocked by an escalated instance`()
    {
        val def = existingDefinition()
        whenever(definitionRepository.findById(def.id)).thenReturn(def)
        whenever(instanceRepository.findActiveForDefinition(def.id))
            .thenReturn(listOf(escalatedInstance(def.id)))

        assertThrows(IllegalStateException::class.java) {
            service.updateDefinition(def.id, UpdateWorkflowDefinitionRequest(name = "New name"))
        }
        verify(definitionRepository, never()).update(any())
    }

    @Test
    fun `delete is blocked by an escalated instance`()
    {
        val def = existingDefinition()
        whenever(definitionRepository.findById(def.id)).thenReturn(def)
        whenever(instanceRepository.findActiveForDefinition(def.id))
            .thenReturn(listOf(escalatedInstance(def.id)))

        assertThrows(IllegalStateException::class.java) {
            service.deleteDefinition(def.id, AdminApprovalContext())
        }
        verify(definitionRepository, never()).update(any())
    }

    private fun existingDefinition() = WorkflowDefinition().apply {
        id = UUID.randomUUID()
        name = "Existing"
        triggerEvent = "exchange.blocking_test"
        scope = WorkflowScope.PERSONAL
        createdByAppUserId = principalId
    }.also {
        whenever(authorizationContextFactory.currentPrincipal())
            .thenReturn(PrincipalRef(PrincipalKind.USER, principalId))
        whenever(authorizationContextFactory.currentContext())
            .thenReturn(AuthorizationContext(activeOrgId = orgId))
        whenever(userRoleService.isAppAdmin(principalId)).thenReturn(true)
    }

    private fun escalatedInstance(definitionId: UUID) = WorkflowInstance().apply {
        id = UUID.randomUUID()
        this.definitionId = definitionId
        status = WorkflowInstanceStatus.ESCALATED
    }
}
