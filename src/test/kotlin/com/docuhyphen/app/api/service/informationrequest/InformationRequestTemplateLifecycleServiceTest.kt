package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestTemplateLifecycleServiceTest
{
    private val actorId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()

    @Test
    fun `retirement records who retired a published version and leaves its configuration readable`()
    {
        val fixture = fixture()

        val result = fixture.service.retireVersion(fixture.definition.id, 1)

        assertEquals(fixture.projected, result)
        assertEquals(InformationRequestTemplateStatus.RETIRED, fixture.source.status)
        assertEquals(actorId, fixture.source.retiredByAppUserId)
        assertNotNull(fixture.source.retiredAt)
        verify(fixture.versionRepository).update(fixture.source)
        verify(fixture.configurationWriter, never()).replaceConfiguration(any(), any())
        verify(fixture.authoringService).recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_RETIRE,
            fixture.definition,
            actorId,
        )
    }

    @Test
    fun `retiring the last frozen version retires a definition with no editable version`()
    {
        val fixture = fixture()

        fixture.service.retireVersion(fixture.definition.id, 1)

        assertEquals(InformationRequestTemplateStatus.RETIRED, fixture.definition.status)
        verify(fixture.definitionRepository).update(fixture.definition)
    }

    @Test
    fun `a new editable version copies a published configuration without capability records`()
    {
        val fixture = fixture()

        fixture.service.createDraftVersion(fixture.definition.id, 1)

        val created = argumentCaptor<InformationRequestTemplateVersion>()
        verify(fixture.versionRepository).save(created.capture())
        assertEquals(2, created.firstValue.versionNumber)
        assertEquals(InformationRequestTemplateStatus.DRAFT, created.firstValue.status)
        assertNull(created.firstValue.publishedAt)
        verify(fixture.configurationWriter).replaceConfiguration(created.firstValue, expectedConfiguration())
        verify(fixture.authoringService).recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_NEW_VERSION,
            fixture.definition,
            actorId,
        )
    }

    @Test
    fun `creating a version locks the definition before deciding whether a draft exists`()
    {
        val fixture = fixture()

        fixture.service.createDraftVersion(fixture.definition.id, 1)

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
            fixture.service.createDraftVersion(fixture.definition.id, 1)
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
        val target = templateDto(UUID.randomUUID(), InformationRequestTemplateStatus.DRAFT)
        val configured = templateDto(target.id, InformationRequestTemplateStatus.DRAFT)
        whenever(fixture.authoringService.createTemplate(request)).thenReturn(target)
        whenever(fixture.authoringService.replaceDraftConfiguration(target.id, expectedConfiguration()))
            .thenReturn(configured)

        val result = fixture.service.cloneTemplate(fixture.definition.id, 1, request)

        assertEquals(configured, result)
        verify(fixture.authoringService).getTemplate(fixture.definition.id)
        verify(fixture.definitionRepository).findByIdForUpdate(fixture.definition.id)
        verify(fixture.authoringService).createTemplate(request)
        verify(fixture.authoringService).replaceDraftConfiguration(target.id, expectedConfiguration())
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
            fixture.service.createDraftVersion(fixture.definition.id, 1)
        }
        assertThrows<IllegalStateException> {
            fixture.service.cloneTemplate(fixture.definition.id, 1, request)
        }

        verify(fixture.authoringService, never()).createTemplate(any())
    }

    private data class Fixture(
        val service: InformationRequestTemplateLifecycleService,
        val authoringService: InformationRequestTemplateAuthoringService,
        val definitionRepository: InformationRequestTemplateDefinitionRepository,
        val versionRepository: InformationRequestTemplateVersionRepository,
        val configurationWriter: InformationRequestTemplateConfigurationWriter,
        val definition: InformationRequestTemplateDefinition,
        val source: InformationRequestTemplateVersion,
        val projected: InformationRequestTemplateDto,
    )

    private fun fixture(
        hasDraft: Boolean = false,
        sourceStatus: InformationRequestTemplateStatus = InformationRequestTemplateStatus.PUBLISHED,
    ): Fixture
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
            publishedByAppUserId = actorId
        }
        val sourceProjection = sourceProjection(source)
        val projected = templateDto(definition.id, definition.status)

        whenever(authoringService.requireMutationContext(definition.id)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(actorId)),
        )
        whenever(authoringService.getTemplate(definition.id)).thenReturn(projected)
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
            } else null,
        )
        whenever(versionRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(projectionLoader.loadVersion(source)).thenReturn(sourceProjection)

        return Fixture(
            InformationRequestTemplateLifecycleService(
                authoringService,
                definitionRepository,
                versionRepository,
                configurationWriter,
                projectionLoader,
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
