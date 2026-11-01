package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateMutationContext
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.informationrequest.template.model.TemplateLifecycleFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestTemplateLifecycleServiceTest
{
    private val actorId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()

    @Test
    fun `retirement records who retired a published version and leaves its configuration readable`()
    {
        val fixture = fixture()

        val result = fixture.service.retireVersion(fixture.definition.id, 1, key())

        assertEquals(fixture.projected, result)
        assertEquals(InformationRequestTemplateStatus.RETIRED, fixture.source.status)
        assertEquals(PrincipalKind.USER, fixture.source.retiredByPrincipalKind)
        assertEquals(actorId, fixture.source.retiredByPrincipalId)
        assertNotNull(fixture.source.retiredAt)
        verify(fixture.versionRepository).update(fixture.source)
        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
        verify(fixture.authoringService).recordTemplateVersionEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_RETIRE,
            fixture.definition,
            fixture.source,
            PrincipalRef.user(actorId),
            null,
        )
    }

    @Test
    fun `retiring the last frozen version retires a definition with no editable version`()
    {
        val fixture = fixture()

        fixture.service.retireVersion(fixture.definition.id, 1, key())

        assertEquals(InformationRequestTemplateStatus.RETIRED, fixture.definition.status)
        verify(fixture.definitionRepository).update(fixture.definition)
    }

    @Test
    fun `retiring the only published version while a draft is open leaves a Template that is only a draft`() {
        val fixture = fixture(hasDraft = true)
        fixture.definition.status = InformationRequestTemplateStatus.PUBLISHED

        fixture.service.retireVersion(fixture.definition.id, 1, key())

        assertEquals(InformationRequestTemplateStatus.DRAFT, fixture.definition.status)
        verify(fixture.definitionRepository).update(fixture.definition)
    }

    @Test
    fun `a new editable version copies a published configuration without capability records`()
    {
        val fixture = fixture()

        fixture.service.createDraftVersion(fixture.definition.id, 1, key())

        val created = argumentCaptor<InformationRequestTemplateVersion>()
        verify(fixture.versionRepository).save(created.capture())
        assertEquals(2, created.firstValue.versionNumber)
        assertEquals(InformationRequestTemplateStatus.DRAFT, created.firstValue.status)
        assertNull(created.firstValue.publishedAt)
        verify(fixture.configurationWriter).replaceConfiguration(created.firstValue, expectedConfiguration())
        verify(fixture.authoringService).recordTemplateVersionEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_NEW_VERSION,
            fixture.definition,
            created.firstValue,
            PrincipalRef.user(actorId),
            fixture.source,
        )
    }

    @Test
    fun `creating a version locks the definition before deciding whether a draft exists`()
    {
        val fixture = fixture()

        fixture.service.createDraftVersion(fixture.definition.id, 1, key())

        inOrder(fixture.definitionRepository, fixture.versionRepository) {
            verify(fixture.definitionRepository).findByIdForUpdate(fixture.definition.id)
            verify(fixture.versionRepository).findDraft(fixture.definition.id)
            verify(fixture.versionRepository).save(any())
        }
    }

    @Test
    fun `a definition cannot acquire a second editable version`()
    {
        val fixture = fixture(hasDraft = true)

        assertThrows<IllegalStateException> {
            fixture.service.createDraftVersion(fixture.definition.id, 1, key())
        }

        verify(fixture.versionRepository, never()).save(any())
        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
    }

    @Test
    fun `a definition clone creates an owner scoped draft from the selected published version`()
    {
        val fixture = fixture()
        val request = CreateInformationRequestTemplateRequest(
            namespace = "process-copy",
            templateKey = "collection-pattern-copy",
            displayName = "Collection pattern copy",
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL,
        )
        val target = targetDefinition()
        val configured = templateDto(target.id, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.authoringService.createDefinition(request)).thenReturn(target)
        whenever(
            fixture.authoringService.configureCopiedDraft(
                target.id,
                expectedConfiguration(),
                fixture.source,
            ),
        ).thenReturn(configured)

        val result = fixture.service.cloneTemplate(fixture.definition.id, 1, request, key())

        assertEquals(configured, result)
        verify(fixture.authoringService).getTemplate(fixture.definition.id)
        verify(fixture.definitionRepository).findByIdForUpdate(fixture.definition.id)
        verify(fixture.authoringService).createDefinition(request)
        verify(fixture.authoringService).configureCopiedDraft(
            target.id,
            expectedConfiguration(),
            fixture.source,
        )
    }

    @Test
    fun `a retirement retried under its idempotency key answers again instead of refusing the retired version`() {
        val fixture = fixture()

        fixture.service.retireVersion(fixture.definition.id, 1, "retire-once")
        val replayed = fixture.service.retireVersion(fixture.definition.id, 1, "retire-once")

        assertEquals(fixture.projected, replayed)
        verify(fixture.versionRepository, times(1)).update(fixture.source)
        verify(fixture.authoringService, times(1)).recordTemplateVersionEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_RETIRE,
            fixture.definition,
            fixture.source,
            PrincipalRef.user(actorId),
            null,
        )
    }

    @Test
    fun `a new version retried under its idempotency key answers again instead of refusing its own draft`() {
        val fixture = fixture()

        fixture.service.createDraftVersion(fixture.definition.id, 1, "new-version-once")
        whenever(fixture.versionRepository.findDraft(fixture.definition.id)).thenReturn(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = fixture.definition.id
                versionNumber = 2
            },
        )
        val replayed = fixture.service.createDraftVersion(fixture.definition.id, 1, "new-version-once")

        assertEquals(fixture.projected, replayed)
        verify(fixture.versionRepository, times(1)).save(any())
    }

    @Test
    fun `a copy retried under its idempotency key answers with the first copy`() {
        val fixture = fixture()
        val request = CreateInformationRequestTemplateRequest(
            namespace = "process-copy",
            templateKey = "collection-pattern-copy",
            displayName = "Collection pattern copy",
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL,
        )
        val target = targetDefinition()
        val configured = templateDto(target.id, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.authoringService.createDefinition(request)).thenReturn(target)
        whenever(fixture.authoringService.configureCopiedDraft(any(), any(), any())).thenReturn(configured)
        whenever(fixture.authoringService.getTemplate(target.id)).thenReturn(configured)

        fixture.service.cloneTemplate(fixture.definition.id, 1, request, "copy-once")
        val replayed = fixture.service.cloneTemplate(fixture.definition.id, 1, request, "copy-once")

        assertEquals(configured, replayed)
        verify(fixture.authoringService, times(1)).createDefinition(request)
    }

    @Test
    fun `only a published source can seed a new version or clone`()
    {
        val fixture = fixture(sourceStatus = InformationRequestTemplateStatus.RETIRED)
        val request = CreateInformationRequestTemplateRequest(
            namespace = "process-copy",
            templateKey = "collection-pattern-copy",
            displayName = "Collection pattern copy",
        )

        assertThrows<IllegalStateException> {
            fixture.service.createDraftVersion(fixture.definition.id, 1, key())
        }
        assertThrows<IllegalStateException> {
            fixture.service.cloneTemplate(fixture.definition.id, 1, request, key())
        }

        verify(fixture.authoringService, never()).createDefinition(any())
    }

    private fun fixture(
        hasDraft: Boolean = false,
        sourceStatus: InformationRequestTemplateStatus = InformationRequestTemplateStatus.PUBLISHED,
    ): TemplateLifecycleFixture
    {
        val authoringService = mock<InformationRequestTemplateAuthoringService>()
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        val configurationWriter = mock<InformationRequestTemplateConfigurationWriter>()
        val projectionLoader = mock<InformationRequestTemplateProjectionLoader>()
        val definition = InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL
            scopeUserId = actorId
            namespace = "process"
            templateKey = "collection-pattern"
            displayName = "Collection pattern"
            status = InformationRequestTemplateStatus.PUBLISHED
        }
        val source = InformationRequestTemplateVersion().apply {
            templateDefinitionId = definition.id
            versionNumber = 1
            status = sourceStatus
            publishedAt = Timestamp.from(Instant.now())
            publishedByPrincipalKind = PrincipalKind.USER
            publishedByPrincipalId = actorId
        }
        val sourceProjection = sourceProjection(source)
        val projected = templateDto(definition.id, definition.status)

        whenever(authoringService.requireMutationContext(definition.id)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(actorId)),
        )
        whenever(authoringService.getTemplate(definition.id)).thenReturn(projected)
        whenever(authoringService.currentAuthor()).thenReturn(PrincipalRef.user(actorId))
        whenever(authoringService.projectTemplate(any())).thenReturn(projected)
        whenever(definitionRepository.findByIdForUpdate(definition.id)).thenReturn(definition)
        whenever(versionRepository.findByNumber(definition.id, 1)).thenReturn(source)
        whenever(versionRepository.findForDefinitions(listOf(definition.id))).thenReturn(listOf(source))
        whenever(versionRepository.findPublished(definition.id)).thenReturn(
            if (sourceStatus == InformationRequestTemplateStatus.PUBLISHED) listOf(source) else emptyList(),
        )
        whenever(versionRepository.findDraft(definition.id)).thenReturn(
            if (hasDraft) InformationRequestTemplateVersion().apply {
                templateDefinitionId = definition.id
                versionNumber = 2
            }
            else null,
        )
        whenever(versionRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(projectionLoader.loadVersion(source)).thenReturn(sourceProjection)

        return TemplateLifecycleFixture(
            InformationRequestTemplateLifecycleService(
                authoringService,
                definitionRepository,
                versionRepository,
                configurationWriter,
                projectionLoader,
                CommandReceiptService(InMemoryTemplateReceiptStore(), CommandReceiptScope()),
            ),
            authoringService,
            definitionRepository,
            versionRepository,
            configurationWriter,
            definition,
            source,
            projected,
        )
    }

    private fun sourceProjection(version: InformationRequestTemplateVersion) =
        InformationRequestTemplateVersionDto(
            id = version.id,
            templateDefinitionId = version.templateDefinitionId,
            versionNumber = version.versionNumber,
            status = version.status,
            schemaVersionId = schemaVersionId,
            sections = listOf(
                InformationRequestTemplateSectionDto(
                    id = UUID.randomUUID(),
                    sectionKey = "collected-data",
                    title = "Collected data",
                    requirements = listOf(
                        InformationRequestTemplateRequirementDto(
                            id = UUID.randomUUID(),
                            templateRequirementId = UUID.randomUUID(),
                            requirementKey = "recorded-note",
                            requirementType = InformationRequestRequirementType.FIELD,
                            prompt = "State the recorded note",
                            responseMode = com.docuhyphen.app.api.model.entity.InformationRequestResponseMode.PROVIDE,
                            requiredness = com.docuhyphen.app.api.model.entity.InformationRequestRequiredness.OPTIONAL,
                            contributorRole = com.docuhyphen.app.api.model.entity.InformationRequestContributorRole.CONTRIBUTOR,
                            reviewPolicy = com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy.NOT_REQUIRED,
                        ),
                    ),
                ),
            ),
            requiredCapabilities = listOf(
                com.docuhyphen.app.api.model.dto.InformationRequestTemplateCapabilityDto(
                    InformationRequestCapability.STRUCTURED_RESPONSE,
                    1,
                ),
            ),
            createdAt = Timestamp.from(Instant.now()),
        )

    private fun expectedConfiguration() = InformationRequestTemplateConfigurationMapper.toRequest(
        sourceProjection(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = UUID(0, 0)
                versionNumber = 1
                status = InformationRequestTemplateStatus.PUBLISHED
            },
        ),
    )

    private fun key(): String = UUID.randomUUID().toString()

    private fun targetDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL
        scopeUserId = actorId
        namespace = "process-copy"
        templateKey = "collection-pattern-copy"
        displayName = "Collection pattern copy"
    }

    private fun templateDto(id: UUID, status: InformationRequestTemplateStatus) =
        InformationRequestTemplateDto(
            id = id,
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL,
            scopeUserId = actorId,
            namespace = "process",
            templateKey = "collection-pattern",
            displayName = "Collection pattern",
            status = status,
            createdAt = Timestamp.from(Instant.now()),
            updatedAt = Timestamp.from(Instant.now()),
        )
}
