package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.FieldDefinitionRepository
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.audit.AuditRecorder
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

class FieldDefinitionTenantBoundaryTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val otherOrganizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private data class Fixture(
        val service: FieldDefinitionService,
        val definitionRepository: FieldDefinitionRepository,
        val contractRepository: FieldContractRepository,
    )

    private fun fixture(
        activeOrganizationId: UUID? = organizationId,
        appAdmin: Boolean = true,
        organizationRole: OrganizationRoleName? = null,
        vararg allowedActions: Action,
    ): Fixture
    {
        val definitionRepository = mock<FieldDefinitionRepository>()
        val contractRepository = mock<FieldContractRepository>()
        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(
            AuthorizationContext(activeOrgId = activeOrganizationId)
        )
        val roles = mock<UserRoleService>()
        whenever(roles.isAppAdmin(principalId)).thenReturn(appAdmin)
        whenever(roles.orgRolesIn(any(), any())).thenAnswer {
            if (organizationRole != null)
                setOf(organizationRole)
            else
                emptySet<OrganizationRoleName>()
        }
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer {
            if (it.getArgument<Action>(1) in allowedActions)
                Decision.Allow()
            else
                Decision.Deny("test-deny", "Denied in test")
        }
        return Fixture(
            FieldDefinitionService(
                fieldDefinitionRepository = definitionRepository,
                fieldContractRepository = contractRepository,
                validator = mock<FieldValueValidator>(),
                typeRegistry = FieldTypeRegistry(),
                authorizationService = authorization,
                authorizationContextFactory = contextFactory,
                userRoleService = roles,
                auditRecorder = mock<AuditRecorder>(),
                subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
            ),
            definitionRepository,
            contractRepository,
        )
    }

    private fun definition(
        scope: FieldScopeKind,
        ownerOrganizationId: UUID? = null,
    ) = FieldDefinition().apply {
        scopeKind = scope
        scopeOrgId = ownerOrganizationId
        namespace = "boundary"
        fieldKey = "reference"
    }

    @Test
    fun `APP_ADMIN without active organization lists only platform fields`()
    {
        val fixture = fixture(activeOrganizationId = null)
        whenever(fixture.definitionRepository.findAllPlatform()).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listDefinitions())
        verify(fixture.definitionRepository).findAllPlatform()
        verify(fixture.definitionRepository, never()).findAllForOrganization(any())
    }

    @Test
    fun `dual-role APP_ADMIN can explicitly list only platform fields`()
    {
        val fixture = fixture(
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )
        whenever(fixture.definitionRepository.findAllPlatform()).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listDefinitions(FieldScopeKind.PLATFORM))
        verify(fixture.definitionRepository).findAllPlatform()
        verify(fixture.definitionRepository, never()).findAllForOrganization(any())
    }

    @Test
    fun `non APP_ADMIN cannot explicitly list platform fields`()
    {
        val fixture = fixture(
            appAdmin = false,
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )

        assertThrows<ForbiddenException> {
            fixture.service.listDefinitions(FieldScopeKind.PLATFORM)
        }
        verify(fixture.definitionRepository, never()).findAllPlatform()
    }

    @Test
    fun `APP_ADMIN without organization membership cannot read organization field`()
    {
        val fixture = fixture(allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW))
        val definition = definition(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.getDefinition(definition.id)
        }
    }

    @Test
    fun `organization field cannot be read through a different active organization`()
    {
        val fixture = fixture(
            activeOrganizationId = otherOrganizationId,
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )
        val definition = definition(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.listContracts(definition.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads organization field through organization permission`()
    {
        val fixture = fixture(
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )
        val definition = definition(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)
        whenever(fixture.contractRepository.findByDefinition(definition.id)).thenReturn(emptyList())

        val result = fixture.service.getDefinition(definition.id)

        assertEquals(definition.id, result.id)
    }

    @Test
    fun `APP_ADMIN can retire platform field`()
    {
        val fixture = fixture(activeOrganizationId = null)
        val definition = definition(FieldScopeKind.PLATFORM)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)
        whenever(fixture.definitionRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(fixture.contractRepository.findByDefinition(definition.id)).thenReturn(emptyList())

        val result = fixture.service.retireDefinition(definition.id)

        assertEquals(com.docuhyphen.app.api.model.entity.FieldLifecycleStatus.RETIRED, result.status)
    }

    @Test
    fun `APP_ADMIN without organization membership cannot add contract to organization field`()
    {
        val fixture = fixture(allowedActions = arrayOf(Action.FIELD_CONFIG_EDIT))
        val definition = definition(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.addContractVersion(
                definition.id,
                FieldContractRequest(
                    valueType = FieldValueType.SHORT_TEXT,
                    label = "Reference",
                ),
            )
        }
        verify(fixture.contractRepository, never()).save(any())
    }

    @Test
    fun `APP_ADMIN with member role cannot mutate organization field`()
    {
        val fixture = fixture(
            organizationRole = OrganizationRoleName.ORG_MEMBER,
            allowedActions = arrayOf(Action.FIELD_CONFIG_EDIT),
        )
        val definition = definition(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)

        assertThrows<ForbiddenException> {
            fixture.service.retireDefinition(definition.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization context defaults new field to platform scope`()
    {
        val fixture = fixture(activeOrganizationId = null)
        whenever(fixture.definitionRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(fixture.contractRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(fixture.contractRepository.findByDefinition(any())).thenReturn(emptyList())

        val result = fixture.service.createDefinition(
            CreateFieldDefinitionRequest(
                namespace = "platform",
                fieldKey = "reference",
                contract = FieldContractRequest(
                    valueType = FieldValueType.SHORT_TEXT,
                    label = "Reference",
                ),
            )
        )

        assertEquals(FieldScopeKind.PLATFORM, result.scopeKind)
        assertEquals(null, result.scopeOrgId)
    }
}
