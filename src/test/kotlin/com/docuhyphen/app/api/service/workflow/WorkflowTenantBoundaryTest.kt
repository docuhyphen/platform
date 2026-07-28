package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkflowTenantBoundaryTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private fun authorizationContext(activeOrgId: UUID? = organizationId) =
        AuthorizationContext(activeOrgId = activeOrgId)

    private fun contextFactory(context: AuthorizationContext): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(principal)
            whenever(it.currentContext()).thenReturn(context)
        }

    private fun roleService(
        appAdmin: Boolean,
        orgAdmin: Boolean,
    ): UserRoleService =
        mock<UserRoleService>().also {
            whenever(it.isAppAdmin(any())).thenReturn(appAdmin)
            whenever(it.isOrgAdminIn(any(), any())).thenReturn(orgAdmin)
        }

    private fun authorizationService(vararg allowedActions: Action): AuthorizationService =
        mock<AuthorizationService>().also {
            whenever(it.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
                val action = invocation.getArgument<Action>(1)
                if (action in allowedActions)
                    Decision.Allow()
                else
                    Decision.Deny("test-deny", "Denied in test")
            }
        }

    private data class ServiceFixture(
        val service: WorkflowDefinitionService,
        val definitionRepository: WorkflowDefinitionRepository,
        val instanceRepository: WorkflowInstanceRepository,
    )

    private fun fixture(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
        allowedActions: Array<out Action> = emptyArray(),
        context: AuthorizationContext = authorizationContext(),
    ): ServiceFixture
    {
        val definitionRepository = mock<WorkflowDefinitionRepository>()
        val instanceRepository = mock<WorkflowInstanceRepository>()

        return ServiceFixture(
            service = WorkflowDefinitionService(
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
                authorizationService = authorizationService(*allowedActions),
                authorizationContextFactory = contextFactory(context),
                userRoleService = roleService(appAdmin, orgAdmin),
                applicabilityEvaluator = mock(),
                workflowSpecValidator = mock(),
                auditRecorder = mock(),
            ),
            definitionRepository = definitionRepository,
            instanceRepository = instanceRepository,
        )
    }

    private fun definition(
        scope: WorkflowScope,
        createdBy: UUID = principalId,
        isTemplate: Boolean = false,
    ) = WorkflowDefinition().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.organizationId = if (scope == WorkflowScope.ORG) organizationId else null
        this.name = "Boundary Workflow"
        this.triggerEvent = "exchange.boundary_test"
        this.stepsJson = WorkflowSpecJson.encode(WorkflowSpec())
        this.isTemplate = isTemplate
    }

    private fun createRequest(
        scope: String? = null,
        isTemplate: Boolean = false,
    ) = CreateWorkflowDefinitionRequest(
        name = "Boundary Workflow",
        triggerEvent = "exchange.boundary_test",
        stepsJson = WorkflowSpecJson.encode(WorkflowSpec()),
        scope = scope,
        isTemplate = isTemplate,
    )

    @Test
    fun `APP_ADMIN list query does not receive unpublished organization workflows without org role`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = false)
        whenever(
            fixture.definitionRepository.findAllAccessibleForCaller(
                principalId,
                organizationId,
                false,
                true,
            )
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listDefinitions(null, null, null, null))
        verify(fixture.definitionRepository).findAllAccessibleForCaller(
            principalId,
            organizationId,
            false,
            true,
        )
    }

    @Test
    fun `APP_ADMIN cannot read another user's personal workflow`()
    {
        val fixture = fixture()
        val definition = definition(WorkflowScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.getDefinition(definition.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot read organization workflow`()
    {
        val fixture = fixture()
        val definition = definition(WorkflowScope.ORG)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.getDefinition(definition.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads organization workflow through organization permission`()
    {
        val fixture = fixture(
            appAdmin = true,
            orgAdmin = true,
            allowedActions = arrayOf(Action.WORKFLOW_VIEW),
        )
        val definition = definition(WorkflowScope.ORG)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        val result = fixture.service.getDefinition(definition.id)

        assertEquals(definition.id, result.id)
    }

    @Test
    fun `APP_ADMIN can read and update APP workflow`()
    {
        val fixture = fixture()
        val definition = definition(WorkflowScope.APP)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)
        whenever(fixture.instanceRepository.findActiveForDefinition(definition.id)).thenReturn(emptyList())
        whenever(fixture.definitionRepository.update(any())).thenAnswer { it.getArgument(0) }

        assertEquals(definition.id, fixture.service.getDefinition(definition.id).id)
        val result = fixture.service.updateDefinition(
            definition.id,
            UpdateWorkflowDefinitionRequest(name = "Changed"),
        )

        assertEquals("Changed", result.name)
    }

    @Test
    fun `APP_ADMIN cannot update another user's personal workflow`()
    {
        val fixture = fixture()
        val definition = definition(WorkflowScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.updateDefinition(
                definition.id,
                UpdateWorkflowDefinitionRequest(name = "Changed"),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot mutate organization workflow`()
    {
        val fixture = fixture()
        val definition = definition(WorkflowScope.ORG)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.updateDefinition(
                definition.id,
                UpdateWorkflowDefinitionRequest(name = "Changed"),
            )
        }
        assertThrows<ForbiddenException> {
            fixture.service.patchPublished(definition.id, true)
        }
        assertThrows<ForbiddenException> {
            fixture.service.patchStatus(definition.id, false)
        }
        assertThrows<ForbiddenException> {
            fixture.service.deleteDefinition(definition.id, AdminApprovalContext())
        }
    }

    @Test
    fun `APP_ADMIN without organization role cannot create organization workflow`()
    {
        val fixture = fixture()

        assertThrows<ForbiddenException> {
            fixture.service.createDefinition(createRequest(scope = WorkflowScope.ORG.name))
        }
    }

    @Test
    fun `APP workflow never inherits active organization identifier`()
    {
        val fixture = fixture()
        whenever(fixture.definitionRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createDefinition(
            createRequest(scope = WorkflowScope.APP.name, isTemplate = true),
        )

        assertEquals(WorkflowScope.APP.name, result.scope)
        assertNull(result.organizationId)
        val saved = argumentCaptor<WorkflowDefinition>()
        verify(fixture.definitionRepository).save(saved.capture())
        assertNull(saved.firstValue.organizationId)
    }

    @Test
    fun `APP_ADMIN cannot mark personal workflow as platform template`()
    {
        val fixture = fixture()
        whenever(fixture.definitionRepository.save(any())).thenAnswer { it.getArgument(0) }

        fixture.service.createDefinition(
            createRequest(scope = WorkflowScope.PERSONAL.name, isTemplate = true),
        )

        val saved = argumentCaptor<WorkflowDefinition>()
        verify(fixture.definitionRepository).save(saved.capture())
        assertFalse(saved.firstValue.isTemplate)
    }

    @Test
    fun `dual-role default creation uses active organization scope`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = true)
        whenever(fixture.definitionRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createDefinition(createRequest())

        assertEquals(WorkflowScope.ORG.name, result.scope)
        assertEquals(organizationId, result.organizationId)
    }

    @Test
    fun `APP_ADMIN cannot clone organization workflow through template flag`()
    {
        val fixture = fixture()
        val source = definition(WorkflowScope.ORG, isTemplate = true)
        whenever(fixture.definitionRepository.findById(source.id)).thenReturn(source)

        assertThrows<ForbiddenException> {
            fixture.service.cloneDefinition(source.id, null)
        }
    }

    @Test
    fun `APP_ADMIN without active organization cannot read tenant workflow instance`()
    {
        val fixture = fixture(context = authorizationContext(activeOrgId = null))
        val instance = WorkflowInstance().apply {
            definitionId = UUID.randomUUID()
            organizationId = organizationId
        }
        whenever(fixture.instanceRepository.findById(instance.id)).thenReturn(instance)

        assertThrows<ForbiddenException> {
            fixture.service.getInstanceDetail(instance.id)
        }
    }

    @Test
    fun `active organization cannot read another organization's workflow instance`()
    {
        val fixture = fixture()
        val instance = WorkflowInstance().apply {
            definitionId = UUID.randomUUID()
            organizationId = UUID.randomUUID()
        }
        whenever(fixture.instanceRepository.findById(instance.id)).thenReturn(instance)

        assertThrows<ForbiddenException> {
            fixture.service.getInstanceDetail(instance.id)
        }
    }
}
