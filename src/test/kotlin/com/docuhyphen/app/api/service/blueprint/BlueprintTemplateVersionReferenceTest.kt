package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.dto.BlueprintInformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateHolder
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionDescription
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.repository.blueprint.BlueprintDefinitionRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintDocumentDefaultRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintFieldDefaultRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintParticipantDefaultRepository
import com.docuhyphen.app.api.repository.documentlibrary.DocumentLibraryRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.blueprint.model.BlueprintTemplateReferenceFixture
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import com.docuhyphen.app.api.service.informationrequest.template.ForeignTemplateVersionFixture
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateReferenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import com.docuhyphen.app.api.service.subscription.*
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class BlueprintTemplateVersionReferenceTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private fun fixture(
        appAdmin: Boolean = false,
        orgAdmin: Boolean = true,
        activeOrgId: UUID? = organizationId,
        allowedActions: Array<out Action> = arrayOf(Action.BLUEPRINT_VIEW, Action.BLUEPRINT_EDIT),
        templateReferenceService: InformationRequestTemplateReferenceService = mock(),
        blueprintSubscriptionGuard: BlueprintSubscriptionGuard = mock(),
    ): BlueprintTemplateReferenceFixture
    {
        val repository = mock<BlueprintDefinitionRepository>()
        val documentDefaults = mock<BlueprintDocumentDefaultRepository>()
        val participantDefaults = mock<BlueprintParticipantDefaultRepository>()
        val fieldDefaults = mock<BlueprintFieldDefaultRepository>()

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

        return BlueprintTemplateReferenceFixture(
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
                blueprintSubscriptionGuard = blueprintSubscriptionGuard,
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

    private fun blueprintUseDenied() = SubscriptionDenialException(
        SubscriptionDenial(
            reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
            planCode = PlanCode.PERSONAL,
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            feature = PlanFeature.BLUEPRINT_USE,
            message = "Blueprints are not included",
        ),
    )

    private fun blueprint(
        scope: BlueprintScope,
        templateVersionId: UUID? = null,
        createdBy: UUID = principalId,
    ): BlueprintDefinition
    {
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
        whenever(fixture.templateReferenceService.requireSelectableVersion(eq(reference.templateVersionId), any()))
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
        verify(fixture.templateReferenceService).requireSelectableVersion(
            reference.templateVersionId,
            InformationRequestTemplateHolder.organization(organizationId),
        )
    }

    @Test
    fun `an organization blueprint cannot name another organization's version`()
    {
        val fixture = fixture()
        val reference = organizationReference(ownerOrganizationId = UUID.randomUUID())
        whenever(fixture.templateReferenceService.requireSelectableVersion(eq(reference.templateVersionId), any()))
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
        whenever(fixture.templateReferenceService.requireSelectableVersion(eq(own.templateVersionId), any()))
            .thenReturn(own)
        whenever(fixture.templateReferenceService.requireSelectableVersion(eq(other.templateVersionId), any()))
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
        verify(fixture.templateReferenceService).requireSelectableVersion(
            own.templateVersionId,
            InformationRequestTemplateHolder.person(principalId),
        )

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
    fun `naming another owner's version is refused as not found before that owner's plan or the version's lifecycle is described`() {
        val foreignOrganization = ForeignTemplateVersionFixture(InformationRequestTemplateScopeKind.ORGANIZATION)
        val foreignPerson = ForeignTemplateVersionFixture(InformationRequestTemplateScopeKind.PERSONAL)
        val organizationBlueprints = fixture(templateReferenceService = foreignOrganization.references)
        val personalBlueprints = fixture(
            orgAdmin = false,
            activeOrgId = null,
            templateReferenceService = foreignPerson.references,
        )

        val organizationRefusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            organizationBlueprints.service.createBlueprint(
                CreateBlueprintRequest(
                    name = "Collection blueprint",
                    configJson = "{}",
                    informationRequestTemplateVersionId = foreignOrganization.versionId,
                ),
                AdminApprovalContext(),
            )
        }
        val personalRefusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            personalBlueprints.service.createBlueprint(
                CreateBlueprintRequest(
                    name = "Collection blueprint",
                    configJson = "{}",
                    informationRequestTemplateVersionId = foreignPerson.versionId,
                ),
                AdminApprovalContext(),
            )
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, organizationRefusal.code)
        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, personalRefusal.code)
        verifyNoInteractions(foreignOrganization.entitlementGuard, foreignPerson.entitlementGuard)
    }

    @Test
    fun `a platform blueprint holds no owner that could name a version`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = false)
        val reference = organizationReference()
        whenever(fixture.templateReferenceService.requireSelectableVersion(eq(reference.templateVersionId), any()))
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
        verify(fixture.templateReferenceService).requireSelectableVersion(
            reference.templateVersionId,
            InformationRequestTemplateHolder.PLATFORM_ONLY,
        )
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
        whenever(fixture.templateReferenceService.requireSelectableVersion(eq(replacement.templateVersionId), any()))
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

        assertEquals(retiredVersionId, result.informationRequestTemplateVersionId)
        verify(fixture.templateReferenceService, never()).requireSelectableVersion(any(), any())
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
        whenever(fixture.templateReferenceService.requireInstantiableVersion(eq(reference.templateVersionId), any()))
            .thenReturn(reference)

        assertEquals(
            reference,
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id),
        )
        verify(fixture.templateReferenceService).requireInstantiableVersion(
            reference.templateVersionId,
            InformationRequestTemplateHolder.organization(organizationId),
        )
    }

    @Test
    fun `a stored blueprint never resolves another owner's version or describes that owner's plan`() {
        val foreign = ForeignTemplateVersionFixture(InformationRequestTemplateScopeKind.ORGANIZATION)
        val fixture = fixture(templateReferenceService = foreign.references)
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = foreign.versionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)

        val resolution = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id)
        }
        val snapshot = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.loadInformationRequestInstantiationSnapshot(
                stored.id,
                principal,
                AuthorizationContext(activeOrgId = organizationId),
            )
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, resolution.code)
        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, snapshot.code)
        verifyNoInteractions(foreign.entitlementGuard)
    }

    @Test
    fun `a deleted or deactivated blueprint refuses a new instantiation with a stable reason`() {
        val fixture = fixture()
        val reference = organizationReference()
        val deleted = blueprint(BlueprintScope.ORG, templateVersionId = reference.templateVersionId).apply {
            isDeleted = true
            isActive = false
        }
        val inactive =
            blueprint(BlueprintScope.ORG, templateVersionId = reference.templateVersionId).apply { isActive = false }
        listOf(deleted, inactive).forEach { whenever(fixture.repository.findById(it.id)).thenReturn(it) }
        whenever(fixture.templateReferenceService.requireInstantiableVersion(eq(reference.templateVersionId), any()))
            .thenReturn(reference)

        listOf(deleted, inactive).forEach { stored ->
            val resolution = assertThrows<InformationRequestTemplateVersionUnavailableException> {
                fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id)
            }
            val snapshot = assertThrows<InformationRequestTemplateVersionUnavailableException> {
                fixture.service.loadInformationRequestInstantiationSnapshot(
                    stored.id,
                    principal,
                    AuthorizationContext(activeOrgId = organizationId),
                )
            }
            assertEquals(InformationRequestTemplateVersionUnavailableException.BLUEPRINT_UNAVAILABLE, resolution.code)
            assertEquals(InformationRequestTemplateVersionUnavailableException.BLUEPRINT_UNAVAILABLE, snapshot.code)
        }
    }

    @Test
    fun `a blueprint its owner's plan no longer lets anyone use refuses a new instantiation before the version is resolved`() {
        val guard = mock<BlueprintSubscriptionGuard>()
        val fixture = fixture(blueprintSubscriptionGuard = guard)
        val reference = organizationReference()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = reference.templateVersionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.templateReferenceService.requireInstantiableVersion(eq(reference.templateVersionId), any()))
            .thenReturn(reference)
        doThrow(blueprintUseDenied()).whenever(guard).requireExistingBlueprintUse(
            principalId,
            BlueprintScope.ORG,
            principalId,
            organizationId,
            organizationId,
        )

        assertThrows<SubscriptionDenialException> {
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(stored.id)
        }
        assertThrows<SubscriptionDenialException> {
            fixture.service.loadInformationRequestInstantiationSnapshot(
                stored.id,
                principal,
                AuthorizationContext(activeOrgId = organizationId),
            )
        }
        verify(fixture.templateReferenceService, never()).requireInstantiableVersion(any(), any())
    }

    @Test
    fun `an unknown blueprint id is refused exactly as a hidden one`() {
        val fixture = fixture()
        val hidden = blueprint(BlueprintScope.PERSONAL).apply { createdByAppUserId = UUID.randomUUID() }
        val unknownId = UUID.randomUUID()
        whenever(fixture.repository.findById(hidden.id)).thenReturn(hidden)

        val unknown = assertThrows<ForbiddenException> {
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(unknownId)
        }
        val other = assertThrows<ForbiddenException> {
            fixture.service.resolveInformationRequestTemplateVersionForInstantiation(hidden.id)
        }

        assertEquals(
            other.message?.replace(hidden.id.toString(), "id"),
            unknown.message?.replace(unknownId.toString(), "id")
        )
    }

    @Test
    fun `a named retired version refuses a new instantiation`()
    {
        val fixture = fixture()
        val retiredVersionId = UUID.randomUUID()
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = retiredVersionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.templateReferenceService.requireInstantiableVersion(eq(retiredVersionId), any()))
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
    fun `a blueprint describes the Template Version it names, even after that Version is retired`() {
        val fixture = fixture()
        val versionId = UUID.randomUUID()
        val templateId = UUID.randomUUID()
        val retiredAt = Timestamp.from(Instant.parse("2026-09-20T00:00:00Z"))
        val stored = blueprint(BlueprintScope.ORG, templateVersionId = versionId)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.templateReferenceService.describeVersion(versionId)).thenReturn(
            InformationRequestTemplateVersionDescription(
                templateVersionId = versionId,
                templateDefinitionId = templateId,
                templateDisplayName = "Collection pattern",
                versionNumber = 2,
                status = InformationRequestTemplateStatus.RETIRED,
                latest = false,
                latestPublishedVersionNumber = 3,
                retiredAt = retiredAt,
            ),
        )

        val described = fixture.service.getBlueprint(stored.id)

        assertEquals(versionId, described.informationRequestTemplateVersionId)
        assertEquals(
            BlueprintInformationRequestTemplateVersionDto(
                templateId = templateId,
                templateDisplayName = "Collection pattern",
                versionNumber = 2,
                status = InformationRequestTemplateStatus.RETIRED,
                latest = false,
                latestPublishedVersionNumber = 3,
                retiredAt = retiredAt,
            ),
            described.informationRequestTemplateVersion,
        )
    }

    @Test
    fun `a blueprint that names no version describes none`() {
        val fixture = fixture()
        val stored = blueprint(BlueprintScope.ORG)
        whenever(fixture.repository.findById(stored.id)).thenReturn(stored)

        assertNull(fixture.service.getBlueprint(stored.id).informationRequestTemplateVersion)
        verify(fixture.templateReferenceService, never()).describeVersion(any())
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

        assertNull(clone.informationRequestTemplateVersionId)
    }
}


