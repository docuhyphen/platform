package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.SchemaVersionRepository
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

class SchemaDefinitionTenantBoundaryTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val otherOrganizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private data class Fixture(
        val service: SchemaDefinitionService,
        val schemaRepository: SchemaDefinitionRepository,
        val versionRepository: SchemaVersionRepository,
        val bindingRepository: SchemaFieldBindingRepository,
        val contractRepository: FieldContractRepository,
        val fieldRepository: FieldDefinitionRepository,
    )

    private fun fixture(
        activeOrganizationId: UUID? = organizationId,
        appAdmin: Boolean = true,
        organizationRole: OrganizationRoleName? = null,
        vararg allowedActions: Action,
    ): Fixture
    {
        val schemaRepository = mock<SchemaDefinitionRepository>()
        val versionRepository = mock<SchemaVersionRepository>()
        val bindingRepository = mock<SchemaFieldBindingRepository>()
        val contractRepository = mock<FieldContractRepository>()
        val fieldRepository = mock<FieldDefinitionRepository>()
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
            SchemaDefinitionService(
                schemaDefinitionRepository = schemaRepository,
                schemaVersionRepository = versionRepository,
                bindingRepository = bindingRepository,
                fieldContractRepository = contractRepository,
                fieldDefinitionRepository = fieldRepository,
                fieldValueValidator = mock<FieldValueValidator>(),
                authorizationService = authorization,
                authorizationContextFactory = contextFactory,
                userRoleService = roles,
                auditRecorder = mock<AuditRecorder>(),
            ),
            schemaRepository,
            versionRepository,
            bindingRepository,
            contractRepository,
            fieldRepository,
        )
    }

    private fun schema(
        scope: FieldScopeKind,
        ownerOrganizationId: UUID? = null,
    ) = SchemaDefinition().apply {
        scopeKind = scope
        scopeOrgId = ownerOrganizationId
        namespace = "boundary"
        schemaKey = "case"
        displayName = "Boundary Case"
    }

    @Test
    fun `APP_ADMIN without active organization lists only platform schemas`()
    {
        val fixture = fixture(activeOrganizationId = null)
        whenever(fixture.schemaRepository.findAllPlatform()).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listSchemas())
        verify(fixture.schemaRepository).findAllPlatform()
        verify(fixture.schemaRepository, never()).findAllForOrganization(any())
    }

    @Test
    fun `dual-role APP_ADMIN can explicitly list only platform schemas`()
    {
        val fixture = fixture(
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )
        whenever(fixture.schemaRepository.findAllPlatform()).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listSchemas(FieldScopeKind.PLATFORM))
        verify(fixture.schemaRepository).findAllPlatform()
        verify(fixture.schemaRepository, never()).findAllForOrganization(any())
    }

    @Test
    fun `non APP_ADMIN cannot explicitly list platform schemas`()
    {
        val fixture = fixture(
            appAdmin = false,
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )

        assertThrows<ForbiddenException> {
            fixture.service.listSchemas(FieldScopeKind.PLATFORM)
        }
        verify(fixture.schemaRepository, never()).findAllPlatform()
    }

    @Test
    fun `APP_ADMIN without organization membership cannot read organization schema`()
    {
        val fixture = fixture(allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW))
        val schema = schema(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)

        assertThrows<ForbiddenException> {
            fixture.service.getSchema(schema.id)
        }
    }

    @Test
    fun `organization schema cannot be resolved through a different active organization`()
    {
        val fixture = fixture(
            activeOrganizationId = otherOrganizationId,
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )
        val schema = schema(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)

        assertThrows<ForbiddenException> {
            fixture.service.getResolvedLatestPublished(schema.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads organization schema through organization permission`()
    {
        val fixture = fixture(
            organizationRole = OrganizationRoleName.ORG_ADMIN,
            allowedActions = arrayOf(Action.FIELD_CONFIG_VIEW),
        )
        val schema = schema(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)

        val result = fixture.service.getSchema(schema.id)

        assertEquals(schema.id, result.id)
    }

    @Test
    fun `APP_ADMIN can create draft version for platform schema`()
    {
        val fixture = fixture(activeOrganizationId = null)
        val schema = schema(FieldScopeKind.PLATFORM)
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)
        whenever(fixture.versionRepository.findMaxVersion(schema.id)).thenReturn(1)
        whenever(fixture.versionRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createDraftVersion(schema.id)

        assertEquals(schema.id, result.id)
        verify(fixture.versionRepository).save(any())
    }

    @Test
    fun `APP_ADMIN without organization membership cannot update organization schema`()
    {
        val fixture = fixture(allowedActions = arrayOf(Action.FIELD_CONFIG_EDIT))
        val schema = schema(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)

        assertThrows<ForbiddenException> {
            fixture.service.updateDraftBindings(schema.id, emptyList())
        }
        verify(fixture.bindingRepository, never()).deleteByVersion(any())
    }

    @Test
    fun `APP_ADMIN with member role cannot publish organization schema`()
    {
        val fixture = fixture(
            organizationRole = OrganizationRoleName.ORG_MEMBER,
            allowedActions = arrayOf(Action.FIELD_CONFIG_PUBLISH),
        )
        val schema = schema(FieldScopeKind.ORGANIZATION, organizationId)
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)

        assertThrows<ForbiddenException> {
            fixture.service.publishDraft(schema.id, null)
        }
    }

    @Test
    fun `platform schema cannot bind organization field`()
    {
        val fixture = fixture(activeOrganizationId = null)
        val schema = schema(FieldScopeKind.PLATFORM)
        val version = SchemaVersion().apply { schemaDefinitionId = schema.id }
        val field = FieldDefinition().apply {
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = organizationId
            namespace = "tenant"
            fieldKey = "secret"
        }
        val contract = FieldContract().apply {
            fieldDefinitionId = field.id
            label = "Secret"
        }
        whenever(fixture.schemaRepository.findById(schema.id)).thenReturn(schema)
        whenever(fixture.versionRepository.findDraft(schema.id)).thenReturn(version)
        whenever(fixture.contractRepository.findById(contract.id)).thenReturn(contract)
        whenever(fixture.fieldRepository.findById(field.id)).thenReturn(field)

        assertThrows<FieldValidationException> {
            fixture.service.updateDraftBindings(
                schema.id,
                listOf(BindingRequest(fieldContractId = contract.id)),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization context defaults new schema to platform scope`()
    {
        val fixture = fixture(activeOrganizationId = null)
        whenever(fixture.schemaRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(fixture.versionRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createSchema(
            CreateSchemaRequest(
                namespace = "platform",
                schemaKey = "case",
                displayName = "Platform Case",
            )
        )

        assertEquals(FieldScopeKind.PLATFORM, result.scopeKind)
        assertEquals(null, result.scopeOrgId)
    }
}
