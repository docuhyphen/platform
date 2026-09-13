package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestTemplatePublicationServiceTest
{
    private val actorId = UUID.randomUUID()

    @Test
    fun `publication records the derived capability contracts before freezing the version`()
    {
        val fixture = fixture(
            derived = listOf(
                InformationRequestCapability.STRUCTURED_RESPONSE,
                InformationRequestCapability.CONDITIONAL_REQUIREMENT,
                InformationRequestCapability.RESPONSE_SUBMISSION,
            ),
        )

        val published = fixture.service.publishTemplate(fixture.definition.id)

        assertEquals(fixture.projected, published)
        val recorded = argumentCaptor<InformationRequestTemplateVersionCapability>()
        verify(fixture.capabilityRepository, org.mockito.kotlin.times(3)).save(recorded.capture())
        assertEquals(
            fixture.derived,
            recorded.allValues.map { it.capabilityKey },
        )
        assertEquals(
            fixture.derived.map { it.contractVersion },
            recorded.allValues.map { it.requiredContractVersion },
        )
        recorded.allValues.forEach { assertEquals(fixture.version.id, it.templateVersionId) }

        assertEquals(InformationRequestTemplateStatus.PUBLISHED, fixture.version.status)
        assertEquals(actorId, fixture.version.publishedByAppUserId)
        assertNotNull(fixture.version.publishedAt)
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, fixture.definition.status)

        inOrder(fixture.capabilityRepository, fixture.versionRepository) {
            verify(fixture.capabilityRepository).flushChanges()
            verify(fixture.versionRepository).update(fixture.version)
        }
    }

    @Test
    fun `an empty draft cannot be published`()
    {
        val fixture = fixture(derived = emptyList())

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.publishTemplate(fixture.definition.id)
        }

        assertEquals(
            "Information request template version 1 configures no requirements",
            refusal.message,
        )
        verify(fixture.capabilityRepository, never()).save(any())
        verify(fixture.versionRepository, never()).update(any())
        verify(fixture.definitionRepository, never()).update(any())
    }

    @Test
    fun `publication requires an editable version`()
    {
        val fixture = fixture(derived = emptyList(), hasDraft = false)

        assertThrows<IllegalStateException> {
            fixture.service.publishTemplate(fixture.definition.id)
        }

        verify(fixture.capabilityRepository, never()).findRequiredCapabilities(any())
    }

    @Test
    fun `publication uses the template owner mutation and audit contracts`()
    {
        val fixture = fixture(derived = listOf(InformationRequestCapability.RESPONSE_ATTESTATION))

        fixture.service.publishTemplate(fixture.definition.id)

        verify(fixture.authoringService).requireMutationContext(fixture.definition.id)
        verify(fixture.authoringService).recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH,
            fixture.definition,
            actorId,
        )
    }

    private data class Fixture(
        val service: InformationRequestTemplatePublicationService,
        val authoringService: InformationRequestTemplateAuthoringService,
        val definitionRepository: InformationRequestTemplateDefinitionRepository,
        val versionRepository: InformationRequestTemplateVersionRepository,
        val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
        val definition: InformationRequestTemplateDefinition,
        val version: InformationRequestTemplateVersion,
        val projected: InformationRequestTemplateDto,
        val derived: List<InformationRequestCapability>,
    )

    private fun fixture(
        derived: List<InformationRequestCapability>,
        hasDraft: Boolean = true,
    ): Fixture
    {
        val definition = InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL
            scopeUserId = actorId
            namespace = "process"
            templateKey = "collection-pattern"
            displayName = "Collection pattern"
        }
        val version = InformationRequestTemplateVersion().apply {
            templateDefinitionId = definition.id
            versionNumber = 1
        }
        val projected = mock<InformationRequestTemplateDto>()
        val authoringService = mock<InformationRequestTemplateAuthoringService>()
        whenever(authoringService.requireMutationContext(definition.id)).thenReturn(
            InformationRequestTemplateMutationContext(definition, PrincipalRef.user(actorId)),
        )
        whenever(authoringService.projectTemplate(definition)).thenReturn(projected)

        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        whenever(versionRepository.findDraftForUpdate(definition.id)).thenReturn(version.takeIf { hasDraft })
        val capabilityRepository = mock<InformationRequestTemplateVersionCapabilityRepository>()
        whenever(capabilityRepository.findRequiredCapabilities(version.id)).thenReturn(derived)

        return Fixture(
            service = InformationRequestTemplatePublicationService(
                authoringService,
                definitionRepository,
                versionRepository,
                capabilityRepository,
            ),
            authoringService = authoringService,
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            capabilityRepository = capabilityRepository,
            definition = definition,
            version = version,
            projected = projected,
            derived = derived,
        )
    }
}
