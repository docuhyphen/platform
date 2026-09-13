package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
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
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateReferenceService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * A blueprint may name one exact published Information Request Template Version, and that name is a
 * decision about what its future instantiations create.
 *
 * Three rules are proved here. A blueprint can only name a Version its own owner holds, because a
 * copy of somebody else's configuration is not something this owner may issue. Changing the name
 * changes nothing that was already created from it. A Version that is retired after being named
 * stays named, so the blueprint remains readable and editable, while creating something new from it
 * is refused with a reason the author can act on.
 */
class BlueprintTemplateVersionReferenceTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private data class ServiceFixture(
        val service: BlueprintDefinitionService,
        val repository: BlueprintDefinitionRepository,
        val templateReferenceService: InformationRequestTemplateReferenceService,
    )

    private fun fixture(
        appAdmin: Boolean = false,
        orgAdmin: Boolean = true,
        activeOrgId: UUID? = organizationId,
        allowedActions: Array<out Action> = arrayOf(Action.BLUEPRINT_VIEW, Action.BLUEPRINT_EDIT),
    ): ServiceFixture
    {
        val repository = mock<BlueprintDefinitionRepository>()
        val documentDefaults = mock<BlueprintDocumentDefaultRepository>()
        val participantDefaults = mock<BlueprintParticipantDefaultRepository>()
        val fieldDefaults = mock<BlueprintFieldDefaultRepository>()
        val templateReferenceService = mock<InformationRequestTemplateReferenceService>()

        whenever(documentDefaults.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        whenever(participantDefaults.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        whenever(fieldDefaults.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val authorizationService = mock<AuthorizationService>().also {
            whenever(it.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
                val action = invocation.getArgument<Action>(1)
                if (action in allowedActions) Decision.Allow() else Decision.Deny("test-deny", "Denied in test")
            }
        }
        val contextFactory = mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(principal)
            whenever(it.currentContext()).thenReturn(AuthorizationContext(activeOrgId = activeOrgId))
        }
        val roleService = mock<UserRoleService>().also {
            whenever(it.isAppAdmin(any())).thenReturn(appAdmin)
            whenever(it.isOrgAdminIn(any(), any())).thenReturn(orgAdmin)
        }

        return ServiceFixture(
            service = BlueprintDefinitionService(
                repository = repository,
                documentDefaultRepository = documentDefaults,
                participantDefaultRepository = participantDefaults,
                fieldDefaultRepository = fieldDefaults,
                documentLibraryRepository = mock<DocumentLibraryRepository>(),
                schemaDefinitionService = mock<SchemaDefinitionService>(),
                adminActionGuardService = mock<AdminActionGuardService>(),
                authorizationService = authorizationService,
                authorizationContextFactory = contextFactory,
                userRoleService = roleService,
                blueprintSubscriptionGuard = mock<BlueprintSubscriptionGuard>(),
                templateReferenceService = templateReferenceService,
            ),
            repository = repository,
            templateReferenceService = templateReferenceService,
        )
    }

    private fun organizationReference(
        versionId: UUID = UUID.randomUUID(),
        ownerOrganizationId: UUID = organizationId,
    ) = InformationRequestTemplateVersionReference(
        templateVersionId = versionId,
        templateDefinitionId = UUID.randomUUID(),
        versionNumber = 1,
        ownerScopeKind = InformationRequestTemplateScopeKind.ORGANIZATION,
        ownerOrganizationId = ownerOrganizationId,
        ownerUserId = null,
    )

    private fun personalReference(
        versionId: UUID = UUID.randomUUID(),
        ownerUserId: UUID = principalId,
    ) = InformationRequestTemplateVersionReference(
        templateVersionId = versionId,
        templateDefinitionId = UUID.randomUUID(),
        versionNumber = 1,
        ownerScopeKind = InformationRequestTemplateScopeKind.PERSONAL,
        ownerOrganizationId = null,
        ownerUserId = ownerUserId,
    )

    private fun blueprint(
        scope: BlueprintScope,
        templateVersionId: UUID? = null,
        createdBy: UUID = principalId,
    ): BlueprintDefinition
    {
        // Held outside the builder because inside it the name would resolve to the entity's own
        // property rather than to the owning organization this test acts for.
        val owningOrganizationId = organizationId
        return BlueprintDefinition().apply {
            this.scope = scope
            this.createdByAppUserId = createdBy
            this.organizationId = if (scope == BlueprintScope.ORG) owningOrganizationId else null
            this.name = "Collection blueprint"
            this.configJson = "{}"
            this.informationRequestTemplateVersionId = templateVersionId
        }
    }

    @Test
    fun `an organization blueprint names a published version its own organization holds`()
    {
        val fixture = fixture()
        val reference = organizationReference()
        whenever(fixture.templateReferenceService.requireSelectableVersion(reference.templateVersionId))
            .thenReturn(reference)

        val result = fixture.service.createBlueprint(
            CreateBlueprintRequest(
                name = "Collection blueprint",
                configJson = "{}",
                informationRequestTemplateVersionId = reference.templateVersionId,
            ),
            AdminApprovalContext(),
        )

        assertEquals(reference.templateVersionId, result.informationRequestTemplateVersionId)
    }

    @Test
    fun `an organization blueprint cannot name another organization's version`()
    {
        val fixture = fixture()
        val reference = organizationReference(ownerOrganizationId = UUID.randomUUID())
        whenever(fixture.templateReferenceService.requireSelectableVersion(reference.templateVersionId))
            .thenReturn(reference)

        assertThrows<ForbiddenException> {
            fixture.service.createBlueprint(
                CreateBlueprintRequest(
                    name = "Collection blueprint",
                    configJson = "{}",
                    informationRequestTemplateVersionId = reference.templateVersionId,
                ),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `a personal blueprint names only its own owner's version`()
    {
        val fixture = fixture(orgAdmin = false, activeOrgId = null)
        val own = personalReference()
        val other = personalReference(ownerUserId = UUID.randomUUID())
        whenever(fixture.templateReferenceService.requireSelectableVersion(own.templateVersionId))
            .thenReturn(own)
        whenever(fixture.templateReferenceService.requireSelectableVersion(other.templateVersionId))
            .thenReturn(other)

        val result = fixture.service.createBlueprint(
            CreateBlueprintRequest(
                name = "Collection blueprint",
                configJson = "{}",
                informationRequestTemplateVersionId = own.templateVersionId,
            ),
            AdminApprovalContext(),
        )
        assertEquals(own.templateVersionId, result.informationRequestTemplateVersionId)

        assertThrows<ForbiddenException> {
            fixture.service.createBlueprint(
                CreateBlueprintRequest(
                    name = "Collection blueprint",
                    configJson = "{}",
                    informationRequestTemplateVersionId = other.templateVersionId,
                ),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `a platform blueprint holds no owner that could name a version`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = false)
        val reference = organizationReference()
        whenever(fixture.templateReferenceService.requireSelectableVersion(reference.templateVersionId))
            .thenReturn(reference)

        assertThrows<ForbiddenException> {
            fixture.service.createBlueprint(
                CreateBlueprintRequest(
                    name = "Platform blueprint",
                    configJson = "{}",
                    scope = BlueprintScope.APP.name,
                    informationRequestTemplateVersionId = reference.templateVersionId,
                ),
                AdminApprovalContext(),
            )
        }
    }

    @Test
    fun `naming a different version replaces the name and nothing else`()
    {
        val fixture = fixture()
        val firstVersionId = UUID.randomUUID()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = firstVersionId).apply {
            schemaDefinitionId = UUID.randomUUID()
        }
        val replacement = organizationReference()
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.templateReferenceService.requireSelectableVersion(replacement.templateVersionId))
            .thenReturn(replacement)

        val result = fixture.service.updateBlueprint(
            stored.id,
            UpdateBlueprintRequest(
                informationRequestTemplateVersionId = replacement.templateVersionId,
            ),
            AdminApprovalContext(),
        )

        assertEquals(replacement.templateVersionId, result.informationRequestTemplateVersionId)
        assertEquals(stored.schemaDefinitionId, result.schemaDefinitionId)
    }

    @Test
    fun `an author can stop naming a version`()
    {
        val fixture = fixture()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = UUID.randomUUID())
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)

        val result = fixture.service.updateBlueprint(
            stored.id,
            UpdateBlueprintRequest(clearInformationRequestTemplateVersion = true),
            AdminApprovalContext(),
        )

        assertNull(result.informationRequestTemplateVersionId)
    }

    @Test
    fun `an update that says nothing about the version leaves a retired one in place`()
    {
        val fixture = fixture()
        val retiredVersionId = UUID.randomUUID()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = retiredVersionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)

        val result = fixture.service.updateBlueprint(
            stored.id,
            UpdateBlueprintRequest(name = "Renamed collection blueprint"),
            AdminApprovalContext(),
        )

        // Editing the rest of a blueprint must not depend on the named Version still being
        // publishable, otherwise a retirement would freeze every blueprint that named it.
        assertEquals(retiredVersionId, result.informationRequestTemplateVersionId)
        verify(fixture.templateReferenceService, never()).requireSelectableVersion(any())
    }

    @Test
    fun `a blueprint that names nothing creates a request from nothing`()
    {
        val fixture = fixture()
        val stored = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)

        assertNull(fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id))
    }

    @Test
    fun `a named published version resolves for a new instantiation`()
    {
        val fixture = fixture()
        val reference = organizationReference()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = reference.templateVersionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.templateReferenceService.requireInstantiableVersion(reference.templateVersionId))
            .thenReturn(reference)

        assertEquals(
            reference.templateVersionId,
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id),
        )
    }

    @Test
    fun `a named retired version refuses a new instantiation`()
    {
        val fixture = fixture()
        val retiredVersionId = UUID.randomUUID()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = retiredVersionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.templateReferenceService.requireInstantiableVersion(retiredVersionId))
            .thenThrow(
                InformationRequestTemplateVersionUnavailableException(
                    InformationRequestTemplateVersionUnavailableException.RETIRED,
                    "Retired in test",
                ),
            )

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id)
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.RETIRED, refusal.code)
    }

    @Test
    fun `a copy for the same owner keeps the named version`()
    {
        val fixture = fixture()
        val versionId = UUID.randomUUID()
        val source = blueprint(BlueprintScope.ORG, templateVersionId = versionId)
        whenever(fixture.repository.findById(source.id)).thenReturn(source)

        val clone = fixture.service.cloneBlueprint(
            source.id,
            CloneBlueprintRequest(targetScope = BlueprintScope.ORG.name),
            AdminApprovalContext(),
        )

        assertEquals(versionId, clone.informationRequestTemplateVersionId)
    }

    @Test
    fun `a copy for another owner starts without a named version`()
    {
        val fixture = fixture()
        val source = blueprint(BlueprintScope.ORG, templateVersionId = UUID.randomUUID())
        whenever(fixture.repository.findById(source.id)).thenReturn(source)

        val clone = fixture.service.cloneBlueprint(
            source.id,
            CloneBlueprintRequest(targetScope = BlueprintScope.PERSONAL.name),
            AdminApprovalContext(),
        )

        // The copy belongs to a different owner, and one owner's reusable configuration is not the
        // other's to issue. Carrying the name across would produce a blueprint whose every
        // instantiation was refused.
        assertNull(clone.informationRequestTemplateVersionId)
    }
}


