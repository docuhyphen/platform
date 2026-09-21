package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * What it takes for something outside the Information Request domain to name one exact Template
 * Version, and what it takes to still create a request from the Version it named.
 *
 * The two questions are deliberately separate. Selecting is a choice an author makes now, so only a
 * currently published Version can be chosen. Instantiating happens repeatedly and much later, by
 * which time the chosen Version may have been retired, and the refusal has to say exactly that so
 * the author knows to choose again.
 */
class InformationRequestTemplateVersionReferenceTest
{
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private data class Fixture(
        val service: InformationRequestTemplateReferenceService,
        val definitionRepository: InformationRequestTemplateDefinitionRepository,
        val versionRepository: InformationRequestTemplateVersionRepository,
        val entitlementGuard: InformationRequestTemplateEntitlementGuard,
    )

    private fun fixture(vararg versions: Pair<InformationRequestTemplateVersion, InformationRequestTemplateDefinition>): Fixture
    {
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        val entitlementGuard = mock<InformationRequestTemplateEntitlementGuard>()

        versions.forEach { (version, definition) ->
            whenever(versionRepository.findById(version.id)).thenReturn(version)
            whenever(definitionRepository.findById(definition.id)).thenReturn(definition)
        }

        return Fixture(
            service = InformationRequestTemplateReferenceService(
                definitionRepository = definitionRepository,
                versionRepository = versionRepository,
                entitlementGuard = entitlementGuard,
            ),
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            entitlementGuard = entitlementGuard,
        )
    }

    private fun organizationDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
        scopeOrgId = organizationId
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    private fun personalDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL
        scopeUserId = userId
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    private fun version(
        definition: InformationRequestTemplateDefinition,
        status: InformationRequestTemplateStatus,
        number: Int = 1,
    ) = InformationRequestTemplateVersion().apply {
        templateDefinitionId = definition.id
        versionNumber = number
        this.status = status
        if (status != InformationRequestTemplateStatus.DRAFT)
        {
            publishedAt = Timestamp.from(Instant.now())
        }
        if (status == InformationRequestTemplateStatus.RETIRED)
        {
            retiredAt = Timestamp.from(Instant.now())
        }
    }

    @Test
    fun `a published version resolves to the exact configuration and the owner that holds it`()
    {
        val definition = organizationDefinition()
        val published = version(definition, InformationRequestTemplateStatus.PUBLISHED, number = 3)
        val fixture = fixture(published to definition)

        val reference = fixture.service.requireSelectableVersion(published.id)

        assertEquals(published.id, reference.templateVersionId)
        assertEquals(definition.id, reference.templateDefinitionId)
        assertEquals(3, reference.versionNumber)
        assertEquals(InformationRequestTemplateScopeKind.ORGANIZATION, reference.ownerScopeKind)
        assertEquals(organizationId, reference.ownerOrganizationId)
        assertNull(reference.ownerUserId)
    }

    @Test
    fun `resolution answers to the gates of the owner that holds the template`()
    {
        val definition = personalDefinition()
        val published = version(definition, InformationRequestTemplateStatus.PUBLISHED)
        val fixture = fixture(published to definition)

        fixture.service.requireSelectableVersion(published.id)

        verify(fixture.entitlementGuard).requireTemplateAccess(
            InformationRequestTemplateScopeKind.PERSONAL,
            null,
            userId,
            null,
        )
    }

    @Test
    fun `a closed gate refuses the reference rather than exposing the version behind it`()
    {
        val definition = organizationDefinition()
        val published = version(definition, InformationRequestTemplateStatus.PUBLISHED)
        val fixture = fixture(published to definition)
        doThrow(ForbiddenException("Denied in test"))
            .whenever(fixture.entitlementGuard)
            .requireTemplateAccess(any(), anyOrNull(), anyOrNull(), anyOrNull())

        assertThrows<ForbiddenException> {
            fixture.service.requireSelectableVersion(published.id)
        }
    }

    @Test
    fun `an unknown version cannot be named`()
    {
        val fixture = fixture()

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.requireSelectableVersion(UUID.randomUUID())
        }

        assertEquals(
            InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
            refusal.code,
        )
    }

    @Test
    fun `a version still being authored cannot be named`()
    {
        val definition = organizationDefinition()
        val draft = version(definition, InformationRequestTemplateStatus.DRAFT)
        val fixture = fixture(draft to definition)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.requireSelectableVersion(draft.id)
        }

        assertEquals(
            InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
            refusal.code,
        )
    }

    @Test
    fun `a retired version cannot be newly named`()
    {
        val definition = organizationDefinition()
        val retired = version(definition, InformationRequestTemplateStatus.RETIRED)
        val fixture = fixture(retired to definition)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.requireSelectableVersion(retired.id)
        }

        assertEquals(
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            refusal.code,
        )
    }

    @Test
    fun `a published version can still be created from`()
    {
        val definition = organizationDefinition()
        val published = version(definition, InformationRequestTemplateStatus.PUBLISHED)
        val fixture = fixture(published to definition)

        assertEquals(
            published.id,
            fixture.service.requireInstantiableVersion(published.id).templateVersionId,
        )
    }

    @Test
    fun `a retired version refuses new creation with a stable reason of its own`()
    {
        val definition = organizationDefinition()
        val retired = version(definition, InformationRequestTemplateStatus.RETIRED)
        val fixture = fixture(retired to definition)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.requireInstantiableVersion(retired.id)
        }

        assertEquals(
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            refusal.code,
        )
    }
}



