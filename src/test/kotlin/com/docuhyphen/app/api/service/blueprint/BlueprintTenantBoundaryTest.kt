package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.repository.blueprint.BlueprintDefinitionRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintDocumentDefaultRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintFieldDefaultRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintParticipantDefaultRepository
import com.docuhyphen.app.api.repository.documentlibrary.DocumentLibraryRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class BlueprintTenantBoundaryTest
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
        val service: BlueprintDefinitionService,
        val repository: BlueprintDefinitionRepository,
    )

    private fun fixture(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
        allowedActions: Array<out Action> = emptyArray(),
        context: AuthorizationContext = authorizationContext(),
    ): ServiceFixture
    {
        val repository = mock<BlueprintDefinitionRepository>()
        val documentDefaults = mock<BlueprintDocumentDefaultRepository>()
        val participantDefaults = mock<BlueprintParticipantDefaultRepository>()
        val fieldDefaults = mock<BlueprintFieldDefaultRepository>()

        whenever(documentDefaults.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        whenever(participantDefaults.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        whenever(fieldDefaults.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())

        return ServiceFixture(
            service = BlueprintDefinitionService(
                repository = repository,
                documentDefaultRepository = documentDefaults,
                participantDefaultRepository = participantDefaults,
                fieldDefaultRepository = fieldDefaults,
                documentLibraryRepository = mock<DocumentLibraryRepository>(),
                schemaDefinitionService = mock<SchemaDefinitionService>(),
                adminActionGuardService = mock<AdminActionGuardService>(),
                authorizationService = authorizationService(*allowedActions),
                authorizationContextFactory = contextFactory(context),
                userRoleService = roleService(appAdmin, orgAdmin),
                blueprintSubscriptionGuard = mock<BlueprintSubscriptionGuard>(),
            ),
            repository = repository,
        )
    }

    private fun blueprint(
        scope: BlueprintScope,
        createdBy: UUID = principalId,
    ) = BlueprintDefinition().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.organizationId = if (scope == BlueprintScope.ORG) organizationId else null
        this.name = "Boundary Blueprint"
        this.configJson = "{}"
    }

    @Test
    fun `APP_ADMIN does not receive unpublished organization blueprints in list query`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = false)
        whenever(
            fixture.repository.findAllAccessibleForCaller(principalId, organizationId, false)
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listBlueprints(null, null, null))
        verify(fixture.repository).findAllAccessibleForCaller(principalId, organizationId, false)
    }

    @Test
    fun `APP_ADMIN cannot read another user's personal blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.getBlueprint(blueprint.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot read organization blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.getBlueprint(blueprint.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads organization blueprint through organization permission`()
    {
        val fixture = fixture(
            appAdmin = true,
            orgAdmin = true,
            allowedActions = arrayOf(Action.BLUEPRINT_VIEW),
        )
        val blueprint = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        val result = fixture.service.getBlueprint(blueprint.id)

        assertEquals(blueprint.id, result.id)
    }

    @Test
    fun `APP_ADMIN cannot update another user's personal blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.updateBlueprint(
                blueprint.id,
                UpdateBlueprintRequest(name = "Changed"),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot update organization blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.updateBlueprint(
                blueprint.id,
                UpdateBlueprintRequest(name = "Changed"),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot publish organization blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.patchPublished(
                blueprint.id,
                PatchBlueprintPublishedRequest(isPublished = true),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot change organization blueprint status`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.patchStatus(
                blueprint.id,
                PatchBlueprintStatusRequest(isActive = false),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot delete organization blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)

        assertThrows<ForbiddenException> {
            fixture.service.deleteBlueprint(blueprint.id, AdminApprovalContext())
        }
    }

    @Test
    fun `APP_ADMIN can update APP blueprint`()
    {
        val fixture = fixture()
        val blueprint = blueprint(BlueprintScope.APP)
        whenever(fixture.repository.findById(blueprint.id)).thenReturn(blueprint)
        whenever(fixture.repository.update(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.updateBlueprint(
            blueprint.id,
            UpdateBlueprintRequest(name = "Changed"),
            AdminApprovalContext(),
        )

        assertEquals("Changed", result.name)
    }

    @Test
    fun `APP_ADMIN without organization role cannot create organization blueprint`()
    {
        val fixture = fixture()

        assertThrows<ForbiddenException> {
            fixture.service.createBlueprint(
                CreateBlueprintRequest(
                    name = "Organization Blueprint",
                    configJson = "{}",
                    scope = BlueprintScope.ORG.name,
                ),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP blueprint never inherits active organization identifier`()
    {
        val fixture = fixture()
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createBlueprint(
            CreateBlueprintRequest(
                name = "Platform Blueprint",
                configJson = "{}",
                scope = BlueprintScope.APP.name,
                isTemplate = true,
            ),
            AdminApprovalContext(),
        )

        assertEquals(BlueprintScope.APP.name, result.scope)
        assertNull(result.organizationId)
        val saved = argumentCaptor<BlueprintDefinition>()
        verify(fixture.repository).save(saved.capture())
        assertNull(saved.firstValue.organizationId)
    }

    @Test
    fun `dual-role default creation uses active organization scope`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = true)
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createBlueprint(
            CreateBlueprintRequest(
                name = "Organization Blueprint",
                configJson = "{}",
            ),
            AdminApprovalContext(),
        )

        assertEquals(BlueprintScope.ORG.name, result.scope)
        assertEquals(organizationId, result.organizationId)
    }

    @Test
    fun `APP_ADMIN without organization role cannot clone into organization scope`()
    {
        val fixture = fixture()
        val source = blueprint(BlueprintScope.APP)
        whenever(fixture.repository.findById(source.id)).thenReturn(source)

        assertThrows<ForbiddenException> {
            fixture.service.cloneBlueprint(
                source.id,
                CloneBlueprintRequest(targetScope = BlueprintScope.ORG.name),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot clone organization blueprint`()
    {
        val fixture = fixture()
        val source = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(source.id)).thenReturn(source)

        assertThrows<ForbiddenException> {
            fixture.service.cloneBlueprint(
                source.id,
                CloneBlueprintRequest(targetScope = BlueprintScope.PERSONAL.name),
                AdminApprovalContext(),
            )
        }
    }
}
