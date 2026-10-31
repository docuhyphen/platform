package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateHolder
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.informationrequest.template.model.TemplateVersionReferenceFixture
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestTemplateVersionReferenceTest
{
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @ParameterizedTest
    @EnumSource(InformationRequestTemplateStatus::class)
    fun `private versions are unavailable for selection and instantiation in both owner scopes`(status: InformationRequestTemplateStatus) {
        listOf(organizationDefinition(), personalDefinition()).forEach { definition ->
            definition.originKind = InformationRequestTemplateOriginKind.AD_HOC_REQUEST
            definition.originRequestId = UUID.randomUUID()
            val privateVersion = version(definition, status)
            val fixture = fixture(privateVersion to definition)

            val selection = assertThrows<InformationRequestTemplateVersionUnavailableException> {
                fixture.service.requireSelectableVersion(privateVersion.id, holderOf(definition))
            }
            val instantiation = assertThrows<InformationRequestTemplateVersionUnavailableException> {
                fixture.service.requireInstantiableVersion(privateVersion.id, holderOf(definition))
            }

            assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, selection.code)
            assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, instantiation.code)
            assertEquals("Information request template version not found: ${privateVersion.id}", selection.message)
            assertEquals("Information request template version not found: ${privateVersion.id}", instantiation.message)
            verifyNoInteractions(fixture.entitlementGuard)
        }
    }

    @ParameterizedTest
    @EnumSource(InformationRequestTemplateStatus::class)
    fun `a version another owner holds reads as unknown and describes neither that owner's plan nor its lifecycle`(
        status: InformationRequestTemplateStatus,
    ) {
        val organizationTemplate = organizationDefinition()
        val personalTemplate = personalDefinition()
        val organizationVersion = version(organizationTemplate, status)
        val personalVersion = version(personalTemplate, status)
        val fixture = fixture(organizationVersion to organizationTemplate, personalVersion to personalTemplate)
        val strangers = listOf(
            organizationVersion to InformationRequestTemplateHolder.organization(UUID.randomUUID()),
            organizationVersion to InformationRequestTemplateHolder.person(userId),
            organizationVersion to InformationRequestTemplateHolder.PLATFORM_ONLY,
            personalVersion to InformationRequestTemplateHolder.person(UUID.randomUUID()),
            personalVersion to InformationRequestTemplateHolder.organization(organizationId),
            personalVersion to InformationRequestTemplateHolder.PLATFORM_ONLY,
        )

        strangers.forEach { (foreign, holder) ->
            val selection = assertThrows<InformationRequestTemplateVersionUnavailableException> {
                fixture.service.requireSelectableVersion(foreign.id, holder)
            }
            val instantiation = assertThrows<InformationRequestTemplateVersionUnavailableException> {
                fixture.service.requireInstantiableVersion(foreign.id, holder)
            }

            listOf(selection, instantiation).forEach { refusal ->
                assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, refusal.code)
                assertEquals("Information request template version not found: ${foreign.id}", refusal.message)
            }
        }
        verifyNoInteractions(fixture.entitlementGuard)
    }

    @Test
    fun `a platform version is held by every owner and answers to no owner's plan`() {
        val definition = organizationDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PLATFORM
            scopeOrgId = null
        }
        val published = version(definition, InformationRequestTemplateStatus.PUBLISHED)
        val fixture = fixture(published to definition)

        listOf(
            InformationRequestTemplateHolder.organization(organizationId),
            InformationRequestTemplateHolder.person(userId),
            InformationRequestTemplateHolder.PLATFORM_ONLY,
        ).forEach { holder ->
            assertEquals(published.id, fixture.service.requireSelectableVersion(published.id, holder).templateVersionId)
        }
        verifyNoInteractions(fixture.entitlementGuard)
    }

    @Test
    fun `an Exchange holds its organization's Templates, or its owner's own when no organization owns it`() {
        val owningOrganizationId = organizationId
        val owningUserId = userId
        val organizationExchange = Exchange().apply {
            ownerOrganizationId = owningOrganizationId
            ownerUserId = owningUserId
        }
        val personalExchange = Exchange().apply { ownerUserId = owningUserId }

        assertEquals(
            InformationRequestTemplateHolder.organization(organizationId),
            InformationRequestTemplateHolder.of(organizationExchange),
        )
        assertEquals(
            InformationRequestTemplateHolder.person(userId),
            InformationRequestTemplateHolder.of(personalExchange)
        )
    }

    private fun fixture(vararg versions: Pair<InformationRequestTemplateVersion, InformationRequestTemplateDefinition>): TemplateVersionReferenceFixture
    {
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        val entitlementGuard = mock<InformationRequestTemplateEntitlementGuard>()

        versions.forEach { (version, definition) ->
            whenever(versionRepository.findById(version.id)).thenReturn(version)
            whenever(definitionRepository.findById(definition.id)).thenReturn(definition)
        }

        return TemplateVersionReferenceFixture(
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

    private fun holderOf(definition: InformationRequestTemplateDefinition) =
        InformationRequestTemplateHolder(definition.scopeOrgId, definition.scopeUserId)

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

        val reference = fixture.service.requireSelectableVersion(published.id, holderOf(definition))

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

        fixture.service.requireSelectableVersion(published.id, holderOf(definition))

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
            fixture.service.requireSelectableVersion(published.id, holderOf(definition))
        }
    }

    @Test
    fun `an unknown version cannot be named`()
    {
        val fixture = fixture()

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.requireSelectableVersion(UUID.randomUUID(), InformationRequestTemplateHolder.person(userId))
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
            fixture.service.requireSelectableVersion(draft.id, holderOf(definition))
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
            fixture.service.requireSelectableVersion(retired.id, holderOf(definition))
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
            fixture.service.requireInstantiableVersion(published.id, holderOf(definition)).templateVersionId,
        )
    }

    @Test
    fun `a retired version refuses new creation with a stable reason of its own`()
    {
        val definition = organizationDefinition()
        val retired = version(definition, InformationRequestTemplateStatus.RETIRED)
        val fixture = fixture(retired to definition)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.requireInstantiableVersion(retired.id, holderOf(definition))
        }

        assertEquals(
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            refusal.code,
        )
    }
}



