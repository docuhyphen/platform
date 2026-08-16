package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.CreateVariableRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.model.entity.VariableDefinition
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.variable.VariableDefinitionRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class VariableTenantBoundaryTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private fun authorizationContext(activeOrgId: UUID? = organizationId) =
        AuthorizationContext(activeOrgId = activeOrgId)

    private fun contextFactory(context: AuthorizationContext = authorizationContext()): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(principal)
            whenever(it.currentContext()).thenReturn(context)
        }

    private fun roleService(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
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
        val service: VariableDefinitionService,
        val repository: VariableDefinitionRepository,
    )

    private fun fixture(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
        allowedActions: Array<out Action> = emptyArray(),
        context: AuthorizationContext = authorizationContext(),
    ): ServiceFixture
    {
        val repository = mock<VariableDefinitionRepository>()
        return ServiceFixture(
            service = VariableDefinitionService(
                repository = repository,
                adminActionGuardService = mock<AdminActionGuardService>(),
                authorizationService = authorizationService(*allowedActions),
                authorizationContextFactory = contextFactory(context),
                userRoleService = roleService(appAdmin, orgAdmin),
                variableSubscriptionGuard = mock<VariableSubscriptionGuard>(),
            ),
            repository = repository,
        )
    }

    private fun variable(
        scope: VariableScope,
        createdBy: UUID = principalId,
    ) = VariableDefinition().apply {
        this.scope = scope
        this.organizationId = if (scope == VariableScope.ORG) organizationId else null
        this.createdByAppUserId = createdBy
        this.key = "BOUNDARY_VARIABLE"
    }

    @Test
    fun `APP_ADMIN without active organization receives no organization variables`()
    {
        val fixture = fixture(context = authorizationContext(activeOrgId = null))

        assertEquals(emptyList<Any>(), fixture.service.listVariables(VariableScope.ORG))
        verify(
            fixture.repository,
            never(),
        ).findByScopeAndOrganizationIdAndIsDeletedFalse(any(), any())
    }

    @Test
    fun `organization variable list is restricted to active organization`()
    {
        val fixture = fixture()
        whenever(
            fixture.repository.findByScopeAndOrganizationIdAndIsDeletedFalse(
                VariableScope.ORG,
                organizationId,
            )
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listVariables(VariableScope.ORG))
        verify(fixture.repository).findByScopeAndOrganizationIdAndIsDeletedFalse(
            VariableScope.ORG,
            organizationId,
        )
    }

    @Test
    fun `personal variable list is restricted to caller`()
    {
        val fixture = fixture()
        whenever(
            fixture.repository.findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(
                VariableScope.PERSONAL,
                principalId,
            )
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listVariables(VariableScope.PERSONAL))
        verify(fixture.repository).findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(
            VariableScope.PERSONAL,
            principalId,
        )
    }

    @Test
    fun `APP_ADMIN without organization role cannot create organization variable`()
    {
        val fixture = fixture()

        assertThrows<ForbiddenException> {
            fixture.service.createVariable(
                CreateVariableRequest(
                    key = "ORG_VARIABLE",
                    scope = VariableScope.ORG.name,
                ),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `dual-role APP_ADMIN creates organization variable through organization role`()
    {
        val fixture = fixture(orgAdmin = true)
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createVariable(
            CreateVariableRequest(
                key = "ORG_VARIABLE",
                scope = VariableScope.ORG.name,
            ),
            AdminApprovalContext(),
        )

        assertEquals(VariableScope.ORG.name, result.scope)
        assertEquals(organizationId, result.organizationId)
    }

    @Test
    fun `personal variable never inherits active organization identifier`()
    {
        val fixture = fixture()
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createVariable(
            CreateVariableRequest(
                key = "PERSONAL_VARIABLE",
                scope = VariableScope.PERSONAL.name,
            ),
            AdminApprovalContext(),
        )

        assertEquals(VariableScope.PERSONAL.name, result.scope)
        assertNull(result.organizationId)
        val saved = argumentCaptor<VariableDefinition>()
        verify(fixture.repository).save(saved.capture())
        assertNull(saved.firstValue.organizationId)
    }

    @Test
    fun `APP_ADMIN cannot update another user's personal variable`()
    {
        val fixture = fixture()
        val variable = variable(VariableScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(variable.id)).thenReturn(variable)

        assertThrows<ForbiddenException> {
            fixture.service.updateVariable(
                variable.id,
                UpdateVariableRequest(defaultValue = "changed"),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot update organization variable`()
    {
        val fixture = fixture()
        val variable = variable(VariableScope.ORG)
        whenever(fixture.repository.findById(variable.id)).thenReturn(variable)

        assertThrows<ForbiddenException> {
            fixture.service.updateVariable(
                variable.id,
                UpdateVariableRequest(defaultValue = "changed"),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `dual-role APP_ADMIN updates organization variable through organization permission`()
    {
        val fixture = fixture(
            orgAdmin = true,
            allowedActions = arrayOf(Action.VARIABLE_EDIT),
        )
        val variable = variable(VariableScope.ORG)
        whenever(fixture.repository.findById(variable.id)).thenReturn(variable)
        whenever(fixture.repository.update(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.updateVariable(
            variable.id,
            UpdateVariableRequest(defaultValue = "changed"),
            AdminApprovalContext(),
        )

        assertEquals("changed", result.defaultValue)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot delete organization variable`()
    {
        val fixture = fixture()
        val variable = variable(VariableScope.ORG)
        whenever(fixture.repository.findById(variable.id)).thenReturn(variable)

        assertThrows<ForbiddenException> {
            fixture.service.deleteVariable(variable.id, AdminApprovalContext())
        }
    }
}
