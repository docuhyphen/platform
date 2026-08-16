package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.model.entity.SequenceDefinition
import com.docuhyphen.app.api.repository.variable.SequenceDefinitionRepository
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SequenceTenantBoundaryTest
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
        val service: SequenceDefinitionService,
        val repository: SequenceDefinitionRepository,
    )

    private fun fixture(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
        allowedActions: Array<out Action> = emptyArray(),
        context: AuthorizationContext = authorizationContext(),
    ): ServiceFixture
    {
        val repository = mock<SequenceDefinitionRepository>()
        return ServiceFixture(
            service = SequenceDefinitionService(
                repository = repository,
                authorizationService = authorizationService(*allowedActions),
                authorizationContextFactory = contextFactory(context),
                userRoleService = roleService(appAdmin, orgAdmin),
                variableSubscriptionGuard = mock<VariableSubscriptionGuard>(),
            ),
            repository = repository,
        )
    }

    private fun sequence() = SequenceDefinition().apply {
        this.organizationId = this@SequenceTenantBoundaryTest.organizationId
        this.name = "Boundary Sequence"
        this.key = "BOUNDARY_SEQUENCE"
    }

    @Test
    fun `APP_ADMIN without active organization receives no sequences`()
    {
        val fixture = fixture(context = authorizationContext(activeOrgId = null))

        assertEquals(emptyList<Any>(), fixture.service.listSequences())
        verify(fixture.repository, never()).findAllByOrganizationIdAndIsDeletedFalse(any())
    }

    @Test
    fun `sequence list is restricted to active organization`()
    {
        val fixture = fixture()
        whenever(
            fixture.repository.findAllByOrganizationIdAndIsDeletedFalse(organizationId)
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listSequences())
        verify(fixture.repository).findAllByOrganizationIdAndIsDeletedFalse(organizationId)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot read sequence`()
    {
        val fixture = fixture()
        val sequence = sequence()
        whenever(fixture.repository.findById(sequence.id)).thenReturn(sequence)

        assertThrows<ForbiddenException> {
            fixture.service.getSequence(sequence.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads sequence through organization permission`()
    {
        val fixture = fixture(
            orgAdmin = true,
            allowedActions = arrayOf(Action.SEQUENCE_VIEW),
        )
        val sequence = sequence()
        whenever(fixture.repository.findById(sequence.id)).thenReturn(sequence)

        val result = fixture.service.getSequence(sequence.id)

        assertEquals(sequence.id, result.id)
    }

    @Test
    fun `APP_ADMIN without organization role cannot create sequence`()
    {
        val fixture = fixture()

        assertThrows<ForbiddenException> {
            fixture.service.createSequence(
                CreateSequenceRequest(
                    name = "Organization Sequence",
                    key = "ORG_SEQUENCE",
                ),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `dual-role APP_ADMIN creates sequence through organization role`()
    {
        val fixture = fixture(orgAdmin = true)
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createSequence(
            CreateSequenceRequest(
                name = "Organization Sequence",
                key = "ORG_SEQUENCE",
            ),
            AdminApprovalContext(),
        )

        assertEquals(organizationId, result.organizationId)
        assertEquals(principalId, result.createdByAppUserId)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot update sequence`()
    {
        val fixture = fixture()
        val sequence = sequence()
        whenever(fixture.repository.findById(sequence.id)).thenReturn(sequence)

        assertThrows<ForbiddenException> {
            fixture.service.updateSequence(
                sequence.id,
                UpdateSequenceRequest(name = "Changed"),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `dual-role APP_ADMIN updates sequence through organization permission`()
    {
        val fixture = fixture(
            orgAdmin = true,
            allowedActions = arrayOf(Action.SEQUENCE_EDIT),
        )
        val sequence = sequence()
        whenever(fixture.repository.findById(sequence.id)).thenReturn(sequence)
        whenever(fixture.repository.update(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.updateSequence(
            sequence.id,
            UpdateSequenceRequest(name = "Changed"),
            AdminApprovalContext(),
        )

        assertEquals("Changed", result.name)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot reset sequence`()
    {
        val fixture = fixture()
        val sequence = sequence()
        sequence.currentValue = 12L
        whenever(fixture.repository.findById(sequence.id)).thenReturn(sequence)

        assertThrows<ForbiddenException> {
            fixture.service.resetCounter(sequence.id, AdminApprovalContext())
        }
        assertEquals(12L, sequence.currentValue)
    }

    @Test
    fun `APP_ADMIN without organization permission cannot delete sequence`()
    {
        val fixture = fixture()
        val sequence = sequence()
        whenever(fixture.repository.findById(sequence.id)).thenReturn(sequence)

        assertThrows<ForbiddenException> {
            fixture.service.deleteSequence(sequence.id, AdminApprovalContext())
        }
    }
}
