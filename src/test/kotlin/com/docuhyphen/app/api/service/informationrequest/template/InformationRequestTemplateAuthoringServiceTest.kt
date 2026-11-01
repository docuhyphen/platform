package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionContent
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.*
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditPayloadPolicy
import com.docuhyphen.app.api.service.informationrequest.template.model.TemplateAuthoringFixture
import com.docuhyphen.app.api.service.subscription.*
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTemplateAuthoringServiceTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val otherOrganizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    @ParameterizedTest
    @ValueSource(strings = ["read", "configure", "mutation-context"])
    fun `private definitions are absent from reusable authoring in both owner scopes`(operation: String) {
        listOf(organizationDefinition(), personalDefinition(principalId)).forEach { definition ->
            val fixture = fixture()
            definition.originKind = InformationRequestTemplateOriginKind.AD_HOC_REQUEST
            definition.originRequestId = UUID.randomUUID()
            whenever(fixture.definitionRepository.findById(definition.id)).thenReturn(definition)
            whenever(fixture.versionRepository.findDraftForUpdate(definition.id)).thenReturn(
                version(definition.id, 2, InformationRequestTemplateStatus.DRAFT),
            )

            val refusal = assertThrows<ForbiddenException> {
                when (operation) {
                    "read" -> fixture.service.getTemplate(definition.id)
                    "configure" -> fixture.service.replaceDraftConfiguration(
                        definition.id,
                        document(),
                        CommandPrecondition.Unconditioned
                    )

                    else -> fixture.service.requireMutationContext(definition.id)
                }
            }

            assertEquals("Access denied to information request template configuration", refusal.message)
            verifyNoInteractions(
                fixture.configurationWriter,
                fixture.projectionLoader,
                fixture.entitlementGuard,
                fixture.auditRecorder
            )
            verify(fixture.definitionRepository, never()).update(any())
        }
    }

    @Test
    fun `an organization template is billed and audited to the organization that owns it`()
    {
        val fixture = fixture()

        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), key())

        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null,
        )
        assertEquals(AuditOwnerScope.Organization(organizationId), fixture.capturedAuditOwner())
    }

    @Test
    fun `a personal template is billed and audited to the person who owns it`()
    {
        val fixture = fixture(activeOrganizationId = null)

        val created = fixture.service.createTemplate(
            createRequest(InformationRequestTemplateScopeKind.PERSONAL),
            key(),
        )

        assertEquals(InformationRequestTemplateScopeKind.PERSONAL, created.scopeKind)
        assertEquals(principalId, created.scopeUserId)
        assertNull(created.scopeOrgId)
        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.PERSONAL, null, principalId, null,
        )
        assertEquals(AuditOwnerScope.Personal(principalId), fixture.capturedAuditOwner())
    }

    @Test
    fun `an active organization sponsors personal template access`()
    {
        val fixture = fixture()

        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.PERSONAL), key())

        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.PERSONAL,
            null,
            principalId,
            organizationId,
        )
    }

    @Test
    fun `a caller with no active organization authors for themselves`()
    {
        val fixture = fixture(activeOrganizationId = null)

        val created = fixture.service.createTemplate(createRequest(scopeKind = null), key())

        assertEquals(InformationRequestTemplateScopeKind.PERSONAL, created.scopeKind)
        assertEquals(principalId, created.scopeUserId)
    }

    @Test
    fun `a caller with an active organization authors for that organization`()
    {
        val fixture = fixture()

        val created = fixture.service.createTemplate(createRequest(scopeKind = null), key())

        assertEquals(InformationRequestTemplateScopeKind.ORGANIZATION, created.scopeKind)
        assertEquals(organizationId, created.scopeOrgId)
        assertNull(created.scopeUserId)
    }

    @Test
    fun `a platform administrator creates a platform Template that no subscription owner answers for`() {
        val fixture = fixture(platformAdministrator = true)

        val created = fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.PLATFORM), key())

        assertEquals(InformationRequestTemplateScopeKind.PLATFORM, created.scopeKind)
        assertNull(created.scopeOrgId)
        assertNull(created.scopeUserId)
        verify(fixture.definitionRepository).findByKey(
            InformationRequestTemplateScopeKind.PLATFORM, null, null, "process", "collection-pattern",
        )
        assertEquals(AuditOwnerScope.Platform, fixture.capturedAuditOwner())
    }

    @Test
    fun `only a platform administrator creates a platform Template`()
    {
        val fixture = fixture(platformAdministrator = false)

        assertThrows<ForbiddenException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.PLATFORM), key())
        }

        verify(fixture.definitionRepository, never()).save(any())
        verify(fixture.versionRepository, never()).save(any())
    }

    @Test
    fun `a platform administrator authors the draft of a platform Template`() {
        val fixture = fixture(platformAdministrator = true)
        val stored = platformDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        val draft = version(stored.id, 2, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.versionRepository.findDraft(stored.id)).thenReturn(draft)
        whenever(fixture.projectionLoader.loadVersion(draft)).thenReturn(projection(draft))

        val context = fixture.service.requireMutationContext(stored.id)
        val projected = fixture.service.getTemplate(stored.id)

        assertEquals(stored, context.definition)
        assertEquals(principal, context.principal)
        assertEquals(draft.id, projected.draftVersion?.id)
    }

    @Test
    fun `nobody but a platform administrator changes a platform Template`() {
        val fixture = fixture(platformAdministrator = false)
        val stored = platformDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)

        assertThrows<ForbiddenException> { fixture.service.requireMutationContext(stored.id) }
        assertThrows<ForbiddenException> {
            fixture.service.replaceDraftConfiguration(
                stored.id,
                document(),
                CommandPrecondition.Unconditioned
            )
        }

        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `platform administrators list unpublished platform Templates and everyone else lists only published ones`() {
        val neverPublished = platformDefinition()
        val published = platformDefinition()
        val versions = listOf(
            version(neverPublished.id, 1, InformationRequestTemplateStatus.DRAFT),
            version(published.id, 1, InformationRequestTemplateStatus.PUBLISHED),
            version(published.id, 2, InformationRequestTemplateStatus.DRAFT),
        )
        val administrator = fixture(platformAdministrator = true)
        val member = fixture(platformAdministrator = false)
        listOf(administrator, member).forEach { fixture ->
            whenever(fixture.definitionRepository.findAllPlatform()).thenReturn(listOf(neverPublished, published))
            whenever(fixture.versionRepository.findForDefinitions(listOf(neverPublished.id, published.id)))
                .thenReturn(versions)
        }

        val administered = administrator.service.listTemplates(InformationRequestTemplateScopeKind.PLATFORM)
        val listed = member.service.listTemplates(InformationRequestTemplateScopeKind.PLATFORM)

        assertEquals(listOf(neverPublished.id, published.id), administered.map { it.id })
        assertEquals(listOf(true, true), administered.map { it.hasEditableVersion })
        assertEquals(listOf(published.id), listed.map { it.id })
        assertEquals(listOf(false), listed.map { it.hasEditableVersion })
        assertEquals(listOf(1), listed.map { it.latestPublishedVersionNumber })
    }

    @Test
    fun `an organization member reads only the published version of an organization Template`() {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        val stored = organizationDefinition()
        val published = version(stored.id, 1, InformationRequestTemplateStatus.PUBLISHED)
        val draft = version(stored.id, 2, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraft(stored.id)).thenReturn(draft)
        whenever(fixture.versionRepository.findLatestPublished(stored.id)).thenReturn(published)
        whenever(fixture.projectionLoader.loadVersion(draft)).thenReturn(projection(draft))
        whenever(fixture.projectionLoader.loadVersion(published)).thenReturn(projection(published))

        val projected = fixture.service.getTemplate(stored.id)

        assertNull(projected.draftVersion)
        assertEquals(published.id, projected.latestPublishedVersion?.id)
        verify(fixture.projectionLoader, never()).loadVersion(draft)
    }

    @Test
    fun `an unknown Template id is refused exactly as another owner's Template is`() {
        val fixture = fixture(activeOrganizationId = null)
        val hidden = personalDefinition(owner = UUID.randomUUID())
        whenever(fixture.definitionRepository.findById(hidden.id)).thenReturn(hidden)

        val unknown = assertThrows<ForbiddenException> { fixture.service.getTemplate(UUID.randomUUID()) }
        val other = assertThrows<ForbiddenException> { fixture.service.getTemplate(hidden.id) }

        assertEquals(other.message, unknown.message)
    }

    @Test
    fun `an organization member cannot open a Template that was never published`() {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        val stored = organizationDefinition()
        val draft = version(stored.id, 1, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraft(stored.id)).thenReturn(draft)
        whenever(fixture.versionRepository.findForDefinitions(listOf(stored.id))).thenReturn(listOf(draft))

        val refusal = assertThrows<ForbiddenException> { fixture.service.getTemplate(stored.id) }

        assertEquals("Access denied to information request template configuration", refusal.message)
        verify(fixture.projectionLoader, never()).loadVersion(draft)
    }

    @Test
    fun `a retired Version can still be read and the Template lists every Version but its draft`() {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        val stored = organizationDefinition()
        val retired = version(stored.id, 1, InformationRequestTemplateStatus.RETIRED)
        val published = version(stored.id, 2, InformationRequestTemplateStatus.PUBLISHED)
        val draft = version(stored.id, 3, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findLatestPublished(stored.id)).thenReturn(published)
        whenever(fixture.versionRepository.findForDefinitions(listOf(stored.id))).thenReturn(
            listOf(
                retired,
                published,
                draft
            )
        )
        whenever(fixture.versionRepository.findByNumber(stored.id, 1)).thenReturn(retired)
        whenever(fixture.versionRepository.findByNumber(stored.id, 3)).thenReturn(draft)
        whenever(fixture.projectionLoader.loadVersion(retired)).thenReturn(projection(retired))
        whenever(fixture.projectionLoader.loadVersion(published)).thenReturn(projection(published))

        val template = fixture.service.getTemplate(stored.id)
        val read = fixture.service.getVersion(stored.id, 1)

        assertEquals(
            listOf(1 to InformationRequestTemplateStatus.RETIRED, 2 to InformationRequestTemplateStatus.PUBLISHED),
            template.versions.map { it.versionNumber to it.status },
        )
        assertEquals(retired.id, read.id)
        assertThrows<IllegalArgumentException> { fixture.service.getVersion(stored.id, 3) }
        verify(fixture.projectionLoader, never()).loadVersion(draft)
    }

    @Test
    fun `an organization administrator reads the draft of an organization Template`() {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_ADMIN)
        val stored = organizationDefinition()
        val draft = version(stored.id, 2, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraft(stored.id)).thenReturn(draft)
        whenever(fixture.projectionLoader.loadVersion(draft)).thenReturn(projection(draft))

        assertEquals(draft.id, fixture.service.getTemplate(stored.id).draftVersion?.id)
    }

    @Test
    fun `an organization member lists no Template that was never published and no editable version`() {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        val neverPublished = organizationDefinition()
        val retired = organizationDefinition()
        val published = organizationDefinition()
        whenever(fixture.definitionRepository.findAllForOrganization(organizationId))
            .thenReturn(listOf(neverPublished, retired, published))
        whenever(fixture.versionRepository.findForDefinitions(listOf(neverPublished.id, retired.id, published.id)))
            .thenReturn(
                listOf(
                    version(neverPublished.id, 1, InformationRequestTemplateStatus.DRAFT),
                    version(retired.id, 1, InformationRequestTemplateStatus.RETIRED),
                    version(published.id, 1, InformationRequestTemplateStatus.PUBLISHED),
                    version(published.id, 2, InformationRequestTemplateStatus.DRAFT),
                ),
            )

        val listed = fixture.service.listTemplates(InformationRequestTemplateScopeKind.ORGANIZATION)

        assertEquals(listOf(retired.id, published.id), listed.map { it.id })
        assertEquals(listOf(false, false), listed.map { it.hasEditableVersion })
    }

    @Test
    fun `an organization template is refused when the caller has no capability for it`()
    {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)

        assertThrows<ForbiddenException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), key())
        }

        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `an organization member can list organization templates without managing them`()
    {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findAllForOrganization(organizationId)).thenReturn(listOf(stored))
        whenever(fixture.versionRepository.findForDefinitions(listOf(stored.id)))
            .thenReturn(listOf(version(stored.id, 1, InformationRequestTemplateStatus.PUBLISHED)))

        val listed = fixture.service.listTemplates(InformationRequestTemplateScopeKind.ORGANIZATION)

        assertEquals(listOf(stored.id), listed.map { it.id })
        verify(fixture.entitlementGuard).requireTemplateAccess(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null,
        )
    }

    @Test
    fun `authoring for an organization the caller has not selected is refused`()
    {
        val fixture = fixture(activeOrganizationId = otherOrganizationId)
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)

        assertThrows<ForbiddenException> {
            fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)
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
            fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)
        }

        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `a template is created with its first editable version`()
    {
        val fixture = fixture()

        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), key())

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
            key(),
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
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), key())
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
            fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)
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

        fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)

        verify(fixture.configurationWriter).replaceConfiguration(draft, document())
        verify(fixture.entitlementGuard).requireTemplateMutation(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null,
        )
        assertEquals(AuditOwnerScope.Organization(organizationId), fixture.capturedAuditOwner())
        assertEquals(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_CONFIGURE.key,
            fixture.capturedAudit().eventTypeKey,
        )
    }

    @Test
    fun `configuring a draft records the author's kind, account label, and session`() {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = InformationRequestTemplateVersion().apply {
            templateDefinitionId = stored.id
            versionNumber = 1
        }
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)

        fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)

        val audit = fixture.capturedAudit()
        assertEquals(principalId, audit.actorId)
        assertEquals(AuditActorKind.HUMAN, audit.actorKind)
        assertEquals(AUTHOR_LABEL, audit.actorLabel)
        assertEquals(AUTHOR_SESSION, audit.sessionId)
    }

    @Test
    fun `a required audit capture failure or an invalid audit draft stops the configuration change`() {
        listOf(
            AuditCaptureFailedException("Synthetic required audit outage", null),
            AuditDraftInvalidException(listOf("Synthetic invalid audit draft")),
        ).forEach { failure ->
            val fixture = fixture()
            val stored = organizationDefinition()
            val draft = InformationRequestTemplateVersion().apply {
                templateDefinitionId = stored.id
                versionNumber = 1
            }
            whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
            whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)
            doThrow(failure).whenever(fixture.auditRecorder).record(any())

            val thrown = assertThrows<RuntimeException> {
                fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)
            }

            assertSame(failure, thrown)
        }
    }

    @Test
    fun `a capture the recorder is permitted to degrade does not stop the configuration change`() {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = InformationRequestTemplateVersion().apply {
            templateDefinitionId = stored.id
            versionNumber = 1
        }
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)
        whenever(fixture.auditRecorder.record(any()))
            .thenReturn(AuditCaptureResult.Degraded(UUID.randomUUID(), "outbox unavailable"))

        fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)

        verify(fixture.configurationWriter).replaceConfiguration(draft, document())
    }

    @Test
    fun `configuring a draft names the changed Version and the revision it now holds`() {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = InformationRequestTemplateVersion().apply {
            templateDefinitionId = stored.id
            versionNumber = 2
            revision = 3
        }
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)

        fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Unconditioned)

        val payload = fixture.capturedAudit().payload
        assertEquals(draft.id.toString(), payload["templateVersionId"])
        assertEquals("2", payload["versionNumber"])
        assertEquals("4", payload["versionRevision"])
        assertEquals("collection-pattern", payload["templateKey"])
        assertEquals(0, InformationRequestAuditPayloadPolicy.projectionOf(payload) { _ -> true }.withheldKeyCount)
    }

    @Test
    fun `a new Version's audit names its source Version in fields the audit history shows`() {
        val fixture = fixture()
        val definition = organizationDefinition()
        val source = version(definition.id, 1, InformationRequestTemplateStatus.PUBLISHED)
        val draft = version(definition.id, 2, InformationRequestTemplateStatus.DRAFT)

        fixture.service.recordTemplateVersionEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_NEW_VERSION,
            definition,
            draft,
            principal,
            source,
        )

        val payload = fixture.capturedAudit().payload
        assertEquals(draft.id.toString(), payload["templateVersionId"])
        assertEquals(source.id.toString(), payload["sourceTemplateVersionId"])
        assertEquals("1", payload["sourceVersionNumber"])
        assertEquals(0, InformationRequestAuditPayloadPolicy.projectionOf(payload) { _ -> true }.withheldKeyCount)
    }

    @Test
    fun `a copied draft's configuration names the Template and Version it was copied from`() {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = version(stored.id, 1, InformationRequestTemplateStatus.DRAFT)
        val source = version(UUID.randomUUID(), 3, InformationRequestTemplateStatus.PUBLISHED)
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)

        fixture.service.configureCopiedDraft(stored.id, document(), source)

        val recorded = fixture.capturedAudit()
        assertEquals(AuditEventType.INFORMATION_REQUEST_TEMPLATE_CONFIGURE.key, recorded.eventTypeKey)
        assertEquals(draft.id.toString(), recorded.payload["templateVersionId"])
        assertEquals(source.templateDefinitionId.toString(), recorded.payload["sourceTemplateDefinitionId"])
        assertEquals(source.id.toString(), recorded.payload["sourceTemplateVersionId"])
        assertEquals("3", recorded.payload["sourceVersionNumber"])
        assertEquals(
            0,
            InformationRequestAuditPayloadPolicy.projectionOf(recorded.payload) { _ -> true }.withheldKeyCount
        )
    }

    @Test
    fun `creating a Template records a Definition event that names no Version`() {
        val fixture = fixture()

        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), key())

        val payload = fixture.capturedAudit().payload
        assertEquals(setOf("namespace", "templateKey", "scopeKind"), payload.keys)
    }

    @Test
    fun `a template event is recorded under the kind of the principal that caused it`() {
        val fixture = fixture()
        val service = PrincipalRef.service(UUID.randomUUID())

        fixture.service.recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_RETIRE,
            organizationDefinition(),
            service
        )

        val audit = fixture.capturedAudit()
        assertEquals(service.id, audit.actorId)
        assertEquals(AuditActorKind.SYSTEM, audit.actorKind)
        assertEquals("service-account:${service.id}", audit.actorLabel)
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

        fixture.service.replaceDraftConfiguration(
            stored.id,
            document(schemaVersionId),
            CommandPrecondition.Unconditioned
        )

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
            fixture.service.replaceDraftConfiguration(
                stored.id,
                document(UUID.randomUUID()),
                CommandPrecondition.Unconditioned
            )
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
        val latest = version(frozenOnly.id, 2, InformationRequestTemplateStatus.PUBLISHED)
        whenever(fixture.versionRepository.findForDefinitions(listOf(withDraft.id, frozenOnly.id)))
            .thenReturn(
                listOf(
                    version(withDraft.id, 1, InformationRequestTemplateStatus.DRAFT),
                    version(frozenOnly.id, 1, InformationRequestTemplateStatus.PUBLISHED),
                    latest,
                ),
            )

        val listed = fixture.service.listTemplates()

        assertEquals(listOf(true, false), listed.map { it.hasEditableVersion })
        assertEquals(listOf(null, 2), listed.map { it.latestPublishedVersionNumber })
        assertEquals(listOf(null, latest.id), listed.map { it.latestPublishedVersionId })
        verify(fixture.versionRepository).findForDefinitions(listOf(withDraft.id, frozenOnly.id))
        verify(fixture.versionRepository, never()).findDraft(any())
        verify(fixture.projectionLoader, never()).loadVersion(any())
    }

    @Test
    fun `platform templates are listed without a customer entitlement`()
    {
        val fixture = fixture()
        val stored = platformDefinition()
        whenever(fixture.definitionRepository.findAllPlatform()).thenReturn(listOf(stored))
        whenever(fixture.versionRepository.findForDefinitions(listOf(stored.id)))
            .thenReturn(listOf(version(stored.id, 1, InformationRequestTemplateStatus.PUBLISHED)))

        val listed = fixture.service.listTemplates(InformationRequestTemplateScopeKind.PLATFORM)

        assertEquals(listOf(InformationRequestTemplateScopeKind.PLATFORM), listed.map { it.scopeKind })
        verify(fixture.entitlementGuard, never()).requireTemplateAccess(
            any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )
    }

    @Test
    fun `platform template detail never exposes an editable version`()
    {
        val fixture = fixture()
        val stored = platformDefinition()
        val published = version(stored.id, 1, InformationRequestTemplateStatus.PUBLISHED)
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findLatestPublished(stored.id)).thenReturn(published)
        whenever(fixture.projectionLoader.loadVersion(published)).thenReturn(projection(published))

        val projected = fixture.service.getTemplate(stored.id)

        assertNull(projected.draftVersion)
        verify(fixture.versionRepository, never()).findDraft(any())
        verify(fixture.entitlementGuard, never()).requireTemplateAccess(
            any(), anyOrNull(), anyOrNull(), anyOrNull(),
        )
    }

    @Test
    fun `reading a template still answers to both gates`()
    {
        val fixture = fixture()
        val stored = organizationDefinition()
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)

        fixture.service.getTemplate(stored.id)

        verify(fixture.entitlementGuard).requireTemplateAccess(
            InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null,
        )
    }

    @Test
    fun `a refused gate stops the mutation`()
    {
        val fixture = fixture()
        doThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    feature = PlanFeature.INFORMATION_REQUESTS,
                    message = "Not released to this owner",
                ),
            ),
        ).whenever(fixture.entitlementGuard)
            .requireTemplateMutation(any(), anyOrNull(), anyOrNull(), anyOrNull())

        assertThrows<SubscriptionDenialException> {
            fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), key())
        }

        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `a draft is replaced only under the tag it was read at`() {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = version(stored.id, 1, InformationRequestTemplateStatus.DRAFT).apply { revision = 3 }
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)

        val stale = assertThrows<CommandPreconditionException> {
            fixture.service.replaceDraftConfiguration(
                stored.id,
                document(),
                CommandPrecondition.ExpectedRevision(RevisionETag.of(draft.id, 2)),
            )
        }
        val unstated = assertThrows<CommandPreconditionException> {
            fixture.service.replaceDraftConfiguration(stored.id, document(), CommandPrecondition.Absent)
        }

        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        assertEquals(RevisionETag.of(draft.id, 3), stale.currentETag)
        assertEquals(CommandPreconditionException.Kind.REQUIRED, unstated.kind)
        assertEquals(3, draft.revision)
        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
        verify(fixture.auditRecorder, never()).record(any())
    }

    @Test
    fun `a replacement under the current tag advances the draft and answers with its next tag`() {
        val fixture = fixture()
        val stored = organizationDefinition()
        val draft = version(stored.id, 1, InformationRequestTemplateStatus.DRAFT).apply { revision = 3 }
        whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
        whenever(fixture.versionRepository.findDraftForUpdate(stored.id)).thenReturn(draft)
        whenever(fixture.versionRepository.findDraft(stored.id)).thenReturn(draft)
        whenever(fixture.projectionLoader.loadVersion(draft)).thenReturn(projection(draft))

        val replaced = fixture.service.replaceDraftConfiguration(
            stored.id,
            document(),
            CommandPrecondition.ExpectedRevision(RevisionETag.of(draft.id, 3)),
        )

        verify(fixture.configurationWriter).replaceConfiguration(draft, document())
        assertEquals(4, draft.revision)
        assertEquals(RevisionETag.of(draft.id, 4), replaced.draftETag)
    }

    @Test
    fun `only a reader who may edit the Template is told the draft tag`()
    {
        val stored = organizationDefinition()
        val published = version(stored.id, 1, InformationRequestTemplateStatus.PUBLISHED)
        val draft = version(stored.id, 2, InformationRequestTemplateStatus.DRAFT).apply { revision = 5 }
        val administrator = fixture(organizationRole = OrganizationRoleName.ORG_ADMIN)
        val member = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        listOf(administrator, member).forEach { fixture ->
            whenever(fixture.definitionRepository.findById(stored.id)).thenReturn(stored)
            whenever(fixture.versionRepository.findDraft(stored.id)).thenReturn(draft)
            whenever(fixture.versionRepository.findLatestPublished(stored.id)).thenReturn(published)
            whenever(fixture.projectionLoader.loadVersion(draft)).thenReturn(projection(draft))
            whenever(fixture.projectionLoader.loadVersion(published)).thenReturn(projection(published))
        }

        assertEquals(RevisionETag.of(draft.id, 5), administrator.service.getTemplate(stored.id).draftETag)
        assertNull(member.service.getTemplate(stored.id).draftETag)
    }

    @Test
    fun `a create retried under its idempotency key answers with the first Template instead of a second`() {
        val fixture = fixture()

        val first = fixture.service.createTemplate(
            createRequest(InformationRequestTemplateScopeKind.ORGANIZATION),
            "create-once"
        )
        val second = fixture.service.createTemplate(
            createRequest(InformationRequestTemplateScopeKind.ORGANIZATION),
            "create-once"
        )

        assertEquals(first.id, second.id)
        assertEquals(1, fixture.savedDefinitions.size)
        assertEquals(1, fixture.receipts.receipts.size)
        assertEquals(ResourceType.INFORMATION_REQUEST_TEMPLATE, fixture.receipts.receipts.single().resultResourceType)
        assertEquals(first.id, fixture.receipts.receipts.single().resultResourceId)
    }

    @Test
    fun `an idempotency key reused for a different Template is refused`() {
        val fixture = fixture()
        fixture.service.createTemplate(createRequest(InformationRequestTemplateScopeKind.ORGANIZATION), "create-once")

        assertThrows<CommandReceiptConflictException> {
            fixture.service.createTemplate(
                createRequest(InformationRequestTemplateScopeKind.ORGANIZATION).copy(templateKey = "other-pattern"),
                "create-once",
            )
        }

        assertEquals(1, fixture.savedDefinitions.size)
    }

    @Test
    fun `bundle references resolve against the frozen Versions the caller can see`() {
        val fixture = fixture(organizationRole = OrganizationRoleName.ORG_MEMBER)
        val organizationTemplate = organizationDefinition()
        val personalTemplate = personalDefinition(principalId).apply { templateKey = "personal-pattern" }
        val platformTemplate = platformDefinition().apply { templateKey = "shared-pattern" }
        val otherKey = organizationDefinition().apply { templateKey = "unreferenced-pattern" }
        whenever(fixture.definitionRepository.findAllForOrganization(organizationId))
            .thenReturn(listOf(organizationTemplate, otherKey))
        whenever(fixture.definitionRepository.findAllForUser(principalId)).thenReturn(listOf(personalTemplate))
        whenever(fixture.definitionRepository.findAllPlatform()).thenReturn(listOf(platformTemplate))
        val stored = listOf(
            version(organizationTemplate.id, 1, InformationRequestTemplateStatus.PUBLISHED),
            version(organizationTemplate.id, 2, InformationRequestTemplateStatus.RETIRED),
            version(organizationTemplate.id, 3, InformationRequestTemplateStatus.DRAFT),
            version(personalTemplate.id, 1, InformationRequestTemplateStatus.PUBLISHED),
            version(platformTemplate.id, 4, InformationRequestTemplateStatus.PUBLISHED),
        )
        whenever(fixture.versionRepository.findForDefinitions(any())).thenAnswer { invocation ->
            val requested = invocation.getArgument<List<UUID>>(0).toSet()
            stored.filter { it.templateDefinitionId in requested }
        }
        whenever(fixture.projectionLoader.loadVersion(any())).thenAnswer { invocation ->
            val version = invocation.getArgument<InformationRequestTemplateVersion>(0)
            projection(version).copy(contentHashSha256 = "hash-${version.versionNumber}")
        }
        val access = RequestAccessContext(principal, AuthorizationContext(activeOrgId = organizationId))

        val contents = fixture.service.frozenVersionContents(
            access,
            setOf("collection-pattern", "personal-pattern", "shared-pattern"),
        )

        assertEquals(
            setOf(
                InformationRequestTemplateVersionContent("collection-pattern", 1, "hash-1"),
                InformationRequestTemplateVersionContent("collection-pattern", 2, "hash-2"),
                InformationRequestTemplateVersionContent("personal-pattern", 1, "hash-1"),
                InformationRequestTemplateVersionContent("shared-pattern", 4, "hash-4"),
            ),
            contents.toSet(),
        )
    }

    @Test
    fun `an organization the caller cannot read contributes no Template Versions to a bundle check`() {
        val fixture = fixture(organizationRole = null)
        whenever(fixture.definitionRepository.findAllForUser(principalId)).thenReturn(emptyList())
        whenever(fixture.definitionRepository.findAllPlatform()).thenReturn(emptyList())
        whenever(fixture.versionRepository.findForDefinitions(any())).thenReturn(emptyList())

        val contents = fixture.service.frozenVersionContents(
            RequestAccessContext(principal, AuthorizationContext(activeOrgId = organizationId)),
            setOf("collection-pattern"),
        )

        assertEquals(emptyList<InformationRequestTemplateVersionContent>(), contents)
        verify(fixture.definitionRepository, never()).findAllForOrganization(any())
        verify(fixture.definitionRepository).findAllForUser(principalId)
    }

    private fun fixture(
        activeOrganizationId: UUID? = organizationId,
        organizationRole: OrganizationRoleName? = OrganizationRoleName.ORG_ADMIN,
        platformAdministrator: Boolean = false,
    ): TemplateAuthoringFixture
    {
        val savedDefinitions = mutableListOf<InformationRequestTemplateDefinition>()
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        whenever(definitionRepository.save(any())).thenAnswer {
            it.getArgument<InformationRequestTemplateDefinition>(0).also(savedDefinitions::add)
        }
        whenever(definitionRepository.findById(any())).thenAnswer { invocation ->
            savedDefinitions.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }
        whenever(definitionRepository.findByIdForUpdate(any())).thenAnswer { invocation ->
            definitionRepository.findById(invocation.getArgument(0))
        }
        val receipts = InMemoryTemplateReceiptStore()
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
            AuthorizationContext(activeOrgId = activeOrganizationId, sessionRef = AUTHOR_SESSION),
        )
        val displays = mock<PrincipalDisplayService>()
        whenever(displays.userLabel(principalId)).thenReturn(AUTHOR_LABEL)

        val roles = mock<UserRoleService>()
        whenever(roles.orgRolesIn(any(), any())).thenAnswer {
            organizationRole?.let(::setOf) ?: emptySet<OrganizationRoleName>()
        }
        whenever(roles.isAppAdmin(principalId)).thenReturn(platformAdministrator)

        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer {
            if (it.getArgument<Action>(1) in TEMPLATE_ACTIONS) Decision.Allow()
            else Decision.Deny("test-deny", "Denied in test")
        }

        return TemplateAuthoringFixture(
            service = InformationRequestTemplateAuthoringService(
                definitionRepository = definitionRepository,
                versionRepository = versionRepository,
                configurationWriter = configurationWriter,
                projectionLoader = projectionLoader,
                authorizationService = authorization,
                authorizationContextFactory = contextFactory,
                userRoleService = roles,
                auditRecorder = auditRecorder,
                actorLabels = InformationRequestAuditActorLabelResolver(displays),
                entitlementGuard = entitlementGuard,
                schemaCompatibility = schemaCompatibility,
                commandReceiptService = CommandReceiptService(receipts, CommandReceiptScope()),
            ),
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            configurationWriter = configurationWriter,
            projectionLoader = projectionLoader,
            entitlementGuard = entitlementGuard,
            schemaCompatibility = schemaCompatibility,
            auditRecorder = auditRecorder,
            receipts = receipts,
            savedDefinitions = savedDefinitions,
        )
    }

    private fun key(): String = UUID.randomUUID().toString()

    private fun version(definitionId: UUID, number: Int, state: InformationRequestTemplateStatus) =
        InformationRequestTemplateVersion().apply {
            templateDefinitionId = definitionId
            versionNumber = number
            status = state
        }

    private fun projection(version: InformationRequestTemplateVersion) = InformationRequestTemplateVersionDto(
        id = version.id,
        templateDefinitionId = version.templateDefinitionId,
        versionNumber = version.versionNumber,
        status = version.status,
        createdAt = version.createdAt,
    )

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

    private fun platformDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.PLATFORM
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    private companion object
    {
        const val AUTHOR_SESSION = "template-author-session"
        const val AUTHOR_LABEL = "Template Author <author@process.test>"
        val TEMPLATE_ACTIONS = setOf(
            Action.INFORMATION_REQUEST_TEMPLATE_VIEW,
            Action.INFORMATION_REQUEST_TEMPLATE_EDIT,
        )
    }
}
