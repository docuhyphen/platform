package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Reusable request configuration is owned by somebody, and who that is decides three separate
 * questions that must all be answered before a draft may be authored: whether the caller is
 * permitted to act for that owner, whether the owner is entitled to the capability commercially, and
 * whether this deployment has released it to them. None of the three stands in for another.
 *
 * These tests hold the front door. They prove that the owner the configuration is billed and audited
 * to is the owner the configuration names rather than whichever organization the caller happens to
 * have selected, that a person authoring for themselves is a resolvable owner rather than a
 * platform-scoped fallback, and that a scope with no owner to hold the gates is refused outright
 * instead of quietly bypassing them.
 */
class InformationRequestTemplateAuthoringServiceTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val otherOrganizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    @Test
    fun `an organization template is billed and audited to the organization that owns it`()
    {
        val fixture = fixture()

        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION))

        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null,
        )
        assertEquals(AuditOwnerScope.Organization(organizationId), fixture.capturedAuditOwner())
    }

    @Test
    fun `a personal template is billed and audited to the person who owns it`()
    {
        val fixture = fixture(activeOrganizationId = null)

        val created = fixture.service.createTemplate(
            createRequest(InformationRequestTemplateScopeKind.PERSONAL),
        )

        assertEquals(InformationRequestTemplateScopeKind.PERSONAL, created.scopeKind)
        assertEquals(principalId, created.scopeUserId)
        assertNull(created.scopeOrgId)
        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.PERSONAL, null, principalId,
        )
        assertEquals(AuditOwnerScope.Personal(principalId), fixture.capturedAuditOwner())
    }

    @Test
    fun `a caller with no active organization authors for themselves`()
    {
        val fixture = fixture(activeOrganizationId = null)

        val created = fixture.service.createTemplate(createRequest(scopeKind = null))

        assertEquals(InformationRequestTemplateScopeKind.PERSONAL, created.scopeKind)
        assertEquals(principalId, created.scopeUserId)
    }

    @Test
    fun `a caller with an active organization authors for that organization`()
    {
        val fixture = fixture()

        val created = fixture.service.createTemplate(createRequest(scopeKind = null))

        assertEquals(InformationRequestTemplateScopeKind.ORGANIZATION, created.scopeKind)
        assertEquals(organizationId, created.scopeOrgId)
        assertNull(created.scopeUserId)
    }

    @Test
    fun `every scope answers to the gates, including one that names no owner`()
    {
        val fixture = fixture()
        doThrow(
            InformationRequestTemplateValidationException("A PLATFORM template has no owner"),
        ).whenever(fixture.entitlementGuard)
            .requireTemplateMutation(any(), anyOrNull(), anyOrNull())

        assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.PLATFORM))
        }

        // The scope reaches the gate rather than being special-cased into an allowance here, which
        // is what keeps one place answering the question for every owner.
        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.PLATFORM, null, null,
        )
        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `an organization template is refused when the caller has no capability for it`()
    {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)

        assertThrows<ForbiddenException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION))
        }

        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `authoring for an organization the caller has not selected is refused`()
    {
        val fixture = fixture(activeOrganizationId = otherOrganizationId)
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)

        assertThrows<ForbiddenException> {
            fixture.service.replaceDraftConfiguration(stored.id, document())
        }

        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `another person's template is not reachable`()
    {
        val fixture = fixture(activeOrganizationId = null)
        val stored = personalDefinition(owner = UUID.randomUUID())
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)

        assertThrows<ForbiddenException> {
            fixture.service.replaceDraftConfiguration(stored.id, document())
        }

        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `a template is created with its first editable version`()
    {
        val fixture = fixture()

        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION))

        val version = argumentCaptor<InformationRequestTemplateVersion>()
        verify(fixture.versionRepository).save(version.capture())
        assertEquals(1, version.firstValue.versionNumber)
        assertNull(version.firstValue.publishedAt)
    }

    @Test
    fun `a template key is stored as one key however it was typed`()
    {
        val fixture = fixture()

        val created = fixture.service.createTemplate(
            CreateInformationRequestTemplateRequest(
                namespace = "  Process  ",
                templateKey = "  Collection-Pattern  ",
                displayName = "  Collection pattern  ",
                description = "   ",
                scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION,
            ),
        )

        assertEquals("process", created.namespace)
        assertEquals("collection-pattern", created.templateKey)
        assertEquals("Collection pattern", created.displayName)
        assertNull(created.description)
    }

    @Test
    fun `a key already used by this owner is refused`()
    {
        val fixture = fixture()
        whenever(
            fixture.definitionRepository.findByKey(
                InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null,
                "process", "collection-pattern",
            ),
        ).thenReturn(organizationDefinition())

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION))
        }

        assertTrue(
            refusal.message.contains("collection-pattern"),
            "The refusal names the key already in use: ${refusal.message}",
        )
        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `a template with no editable version cannot be configured`()
    {
        val fixture = fixture()
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(null)

        assertThrows<IllegalStateException> {
            fixture.service.replaceDraftConfiguration(stored.id, document())
        }

        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `configuring a draft records the change against the owner`()
    {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = InformationRequestTemplateVersion().apply {
            templateDefinitionId = stored.id
            versionNumber = 1
        }
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)

        fixture.service.replaceDraftConfiguration(stored.id, document())

        verify(fixture.configurationWriter).replaceConfiguration(draft, document())
        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null,
        )
        assertEquals(AuditOwnerScope.Organization(organizationId), fixture.capturedAuditOwner())
        assertEquals(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_CONFIGURE.key,
            fixture.capturedAudit().eventTypeKey,
        )
    }

    @Test
    fun `a named schema version is checked against the owner before anything is written`()
    {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = InformationRequestTemplateVersion().apply {
            templateDefinitionId = stored.id
            versionNumber = 1
        }
        val schemaVersionId = UUID.randomUUID()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)

        fixture.service.replaceDraftConfiguration(stored.id, document(schemaVersionId))

        verify(fixture.schemaCompatibility).requireUsable(stored, schemaVersionId)
    }

    @Test
    fun `a schema the owner cannot use stops the configuration from being written`()
    {
        val fixture = fixture()
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        doThrow(
            InformationRequestTemplateValidationException("Schema version belongs to a different owner"),
        ).whenever(fixture.schemaCompatibility).requireUsable(any(), anyOrNull())

        assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.replaceDraftConfiguration(stored.id, document(UUID.randomUUID()))
        }

        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `a list reports what an author needs without reading every configuration`()
    {
        val fixture = fixture()
        val withDraft = organizationDefinition()
        val frozenOnly = organizationDefinition()
        whenever(fixture.definitionRepository.findAllForOrganization(organizationId))
            .thenReturn(listOf(withDraft, frozenOnly))
        whenever(fixture.versionRepository.findForDefinitions(listOf(withDraft.id, frozenOnly.id)))
            .thenReturn(
                listOf(
                    version(withDraft.id, 1, InformationRequestTemplateStatus.DRAFT),
                    version(frozenOnly.id, 1, InformationRequestTemplateStatus.PUBLISHED),
                    version(frozenOnly.id, 2, InformationRequestTemplateStatus.PUBLISHED),
                ),
            )

        val listed = fixture.service.listTemplates()

        assertEquals(listOf(true, false), listed.map { it.hasEditableVersion })
        assertEquals(listOf(null, 2), listed.map { it.latestPublishedVersionNumber })
        // One read of the versions of every listed Template, rather than one read per Template and
        // a whole configuration projection behind each of them.
        verify(fixture.versionRepository).findForDefinitions(listOf(withDraft.id, frozenOnly.id))
        verify(fixture.versionRepository, never()).findDraft(any())
        verify(fixture.projectionLoader, never()).loadVersion(any())
    }

    @Test
    fun `reading a template still answers to both gates`()
    {
        val fixture = fixture()
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)

        fixture.service.getTemplate(stored.id)

        verify(fixture.entitlementGuard).requireTemplateAccess(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null,
        )
    }

    @Test
    fun `a refused gate stops the mutation`()
    {
        val fixture = fixture()
        doThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.FEATURE_NOT_RELEASED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    feature = PlanFeature.INFORMATION_REQUESTS,
                    message = "Not released to this owner",
                ),
            ),
        ).whenever(fixture.entitlementGuard)
            .requireTemplateMutation(any(), anyOrNull(), anyOrNull())

        assertThrows<SubscriptionDenialException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION))
        }

        verify(fixture.definitionRepository, never()).save(any())
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private data class Fixture(
        val service: InformationRequestTemplateAuthoringService,
        val definitionRepository: InformationRequestTemplateDefinitionRepository,
        val versionRepository: InformationRequestTemplateVersionRepository,
        val configurationWriter: InformationRequestTemplateConfigurationWriter,
        val projectionLoader: InformationRequestTemplateProjectionLoader,
        val entitlementGuard: InformationRequestTemplateEntitlementGuard,
        val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
        val auditRecorder: AuditRecorder,
    )
    {
        fun capturedAudit(): AuditEventDraft
        {
            val draft = argumentCaptor<AuditEventDraft>()
            verify(auditRecorder).record(draft.capture())
            return draft.firstValue
        }

        fun capturedAuditOwner(): AuditOwnerScope = capturedAudit().owner
    }

    private fun fixture(
        activeOrganizationId: UUID? = organizationId,
        organizationRole: OrganizationRoleName? = OrganizationRoleName.ORG_ADMIN,
    ): Fixture
    {
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        whenever(definitionRepository.save(any())).thenAnswer { it.getArgument(0) }
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        whenever(versionRepository.save(any())).thenAnswer { it.getArgument(0) }
        val configurationWriter = mock<InformationRequestTemplateConfigurationWriter>()
        val projectionLoader = mock<InformationRequestTemplateProjectionLoader>()
        val entitlementGuard = mock<InformationRequestTemplateEntitlementGuard>()
        val schemaCompatibility = mock<InformationRequestTemplateSchemaCompatibility>()
        val auditRecorder = mock<AuditRecorder>()

        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(
            AuthorizationContext(activeOrgId = activeOrganizationId),
        )

        val roles = mock<UserRoleService>()
        whenever(roles.orgRolesIn(any(), any())).thenAnswer {
            organizationRole?.let(::setOf) ?: emptySet<OrganizationRoleName>()
        }

        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer {
            if (it.getArgument<Action>(1) in TEMPLATE_ACTIONS) Decision.Allow()
            else Decision.Deny("test-deny", "Denied in test")
        }

        return Fixture(
            service = InformationRequestTemplateAuthoringService(
                definitionRepository = definitionRepository,
                versionRepository = versionRepository,
                configurationWriter = configurationWriter,
                projectionLoader = projectionLoader,
                authorizationService = authorization,
                authorizationContextFactory = contextFactory,
                userRoleService = roles,
                auditRecorder = auditRecorder,
                entitlementGuard = entitlementGuard,
                schemaCompatibility = schemaCompatibility,
            ),
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            configurationWriter = configurationWriter,
            projectionLoader = projectionLoader,
            entitlementGuard = entitlementGuard,
            schemaCompatibility = schemaCompatibility,
            auditRecorder = auditRecorder,
        )
    }

    private fun version(definitionId: UUID, number: Int, state: InformationRequestTemplateStatus) =
        InformationRequestTemplateVersion().apply {
            templateDefinitionId = definitionId
            versionNumber = number
            status = state
        }

    private fun createRequest(scopeKind: InformationRequestTemplateScopeKind?) =
        CreateInformationRequestTemplateRequest(
            namespace = "process",
            templateKey = "collection-pattern",
            displayName = "Collection pattern",
            scopeKind = scopeKind,
        )

    private fun document(schemaVersionId: UUID? = null) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "collected-data",
                title = "Collected data",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = "recorded-note",
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = "State the recorded note",
                    ),
                ),
            ),
        ),
    )

    private fun organizationDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
        scopeOrgId = organizationId
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    private fun personalDefinition(owner: UUID) = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL
        scopeUserId = owner
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    private companion object
    {
        val TEMPLATE_ACTIONS = setOf(
            Action.INFORMATION_REQUEST_TEMPLATE_VIEW,
            Action.INFORMATION_REQUEST_TEMPLATE_EDIT,
        )
    }
}
