package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestTemplateVersionDescriptionTest {
    private val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
    private val versionRepository = mock<InformationRequestTemplateVersionRepository>()
    private val entitlementGuard = mock<InformationRequestTemplateEntitlementGuard>()
    private val service =
        InformationRequestTemplateReferenceService(definitionRepository, versionRepository, entitlementGuard)
    private val definition = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
        scopeOrgId = UUID.randomUUID()
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    @Test
    fun `a retired pinned Version is described with its Template, number, and retirement`() {
        val retired = stored(2, InformationRequestTemplateStatus.RETIRED)
        val current = stored(3, InformationRequestTemplateStatus.PUBLISHED)
        whenever(versionRepository.findLatestPublished(definition.id)).thenReturn(current)

        val described = service.describeVersion(retired.id)

        assertNotNull(described)
        assertEquals(retired.id, described!!.templateVersionId)
        assertEquals(definition.id, described.templateDefinitionId)
        assertEquals("Collection pattern", described.templateDisplayName)
        assertEquals(2, described.versionNumber)
        assertEquals(InformationRequestTemplateStatus.RETIRED, described.status)
        assertEquals(false, described.latest)
        assertEquals(3, described.latestPublishedVersionNumber)
        assertEquals(retired.retiredAt, described.retiredAt)
        verifyNoInteractions(entitlementGuard)
    }

    @Test
    fun `a published Version is described as latest only while no later Version is published`() {
        val earlier = stored(1, InformationRequestTemplateStatus.PUBLISHED)
        val later = stored(2, InformationRequestTemplateStatus.PUBLISHED)
        whenever(versionRepository.findLatestPublished(definition.id)).thenReturn(later)

        assertEquals(false, service.describeVersion(earlier.id)?.latest)
        assertEquals(true, service.describeVersion(later.id)?.latest)
        assertEquals(2, service.describeVersion(earlier.id)?.latestPublishedVersionNumber)
    }

    @Test
    fun `an unknown or private Version has no description`() {
        val privateVersion = stored(1, InformationRequestTemplateStatus.PUBLISHED)
        definition.originKind = InformationRequestTemplateOriginKind.AD_HOC_REQUEST
        definition.originRequestId = UUID.randomUUID()

        assertNull(service.describeVersion(UUID.randomUUID()))
        assertNull(service.describeVersion(privateVersion.id))
    }

    private fun stored(number: Int, state: InformationRequestTemplateStatus): InformationRequestTemplateVersion {
        val version = InformationRequestTemplateVersion().apply {
            templateDefinitionId = definition.id
            versionNumber = number
            status = state
            publishedAt = Timestamp.from(Instant.parse("2026-09-01T00:00:00Z"))
            if (state == InformationRequestTemplateStatus.RETIRED) {
                retiredAt = Timestamp.from(Instant.parse("2026-09-20T00:00:00Z"))
            }
        }
        whenever(versionRepository.findById(version.id)).thenReturn(version)
        whenever(definitionRepository.findById(definition.id)).thenReturn(definition)
        return version
    }
}
