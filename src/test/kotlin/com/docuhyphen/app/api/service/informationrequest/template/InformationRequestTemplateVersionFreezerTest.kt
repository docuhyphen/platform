package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAttestationPolicyDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.template.model.TemplateVersionFreezerFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestTemplateVersionFreezerTest {
    private val actor = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID.randomUUID())

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `stored drafts assigning work to a subject identity cannot be published`(confirmation: Boolean) {
        val requirement =
            requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION).copy(
                contributorRole = if (confirmation) InformationRequestContributorRole.CONTRIBUTOR else InformationRequestContributorRole.SUBJECT,
                attestationPolicy = if (confirmation) InformationRequestTemplateAttestationPolicyDto(
                    id = UUID.randomUUID(),
                    requiredRoles = listOf(
                        InformationRequestContributorRole.ATTESTOR,
                        InformationRequestContributorRole.SUBJECT
                    ),
                    ordering = InformationRequestAttestationOrdering.ANY_ORDER,
                    minimumAssentCount = 2,
                    minimumAuthenticationStrength = InformationRequestAuthenticationStrength.VERIFIED_CONTACT,
                    externalSignatureReference = InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED,
                )
                else null,
            )
        val fixture = fixture(
            derived = listOf(InformationRequestCapability.RESPONSE_SUBMISSION),
            projected = projection(listOf(section("confirmations", requirement))),
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.freezer.freeze(fixture.definition, fixture.version, actor)
        }

        assertEquals("confirmations", refusal.sectionKey)
        assertEquals("recorded-assertion", refusal.requirementKey)
        assertUnchanged(fixture)
    }

    @Test
    fun `freezing records each derived capability at its contract version before the version freezes`() {
        val fixture = fixture(
            derived = listOf(
                InformationRequestCapability.STRUCTURED_RESPONSE,
                InformationRequestCapability.CONDITIONAL_REQUIREMENT,
                InformationRequestCapability.RESPONSE_SUBMISSION,
            ),
        )

        fixture.freezer.freeze(fixture.definition, fixture.version, actor)

        val recorded = argumentCaptor<InformationRequestTemplateVersionCapability>()
        verify(fixture.capabilityRepository, times(3)).save(recorded.capture())
        assertEquals(fixture.derived, recorded.allValues.map { it.capabilityKey })
        assertEquals(fixture.derived.map { it.contractVersion }, recorded.allValues.map { it.requiredContractVersion })
        recorded.allValues.forEach { assertEquals(fixture.version.id, it.templateVersionId) }
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, fixture.version.status)
        assertEquals(actor.kind, fixture.version.publishedByPrincipalKind)
        assertEquals(actor.id, fixture.version.publishedByPrincipalId)
        assertNotNull(fixture.version.publishedAt)
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, fixture.definition.status)
        assertEquals(fixture.version.publishedAt, fixture.definition.updatedAt)
        inOrder(fixture.capabilityRepository, fixture.versionRepository, fixture.definitionRepository) {
            verify(fixture.capabilityRepository).flushChanges()
            verify(fixture.versionRepository).update(fixture.version)
            verify(fixture.definitionRepository).update(fixture.definition)
        }
    }

    @Test
    fun `a version that configures nothing is refused before anything is recorded`() {
        val fixture = fixture(derived = emptyList())

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.freezer.freeze(fixture.definition, fixture.version, actor)
        }

        assertEquals("Information request template version 3 configures no requirements", refusal.message)
        assertUnchanged(fixture)
    }

    @Test
    fun `a version that cannot be judged is refused by naming the requirement`() {
        val undocumented = requirement("supporting-file", InformationRequestRequirementType.DOCUMENT)
        val fixture = fixture(
            derived = listOf(InformationRequestCapability.DOCUMENT_EVIDENCE),
            projected = projection(listOf(section("evidence", undocumented))),
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.freezer.freeze(fixture.definition, fixture.version, actor)
        }

        assertEquals("evidence", refusal.sectionKey)
        assertEquals("supporting-file", refusal.requirementKey)
        assertUnchanged(fixture)
    }

    @Test
    fun `a schema version the owner can no longer use stops the freeze`() {
        val fixture = fixture(derived = listOf(InformationRequestCapability.STRUCTURED_RESPONSE))
        fixture.version.schemaVersionId = UUID.randomUUID()
        doThrow(InformationRequestTemplateValidationException("Schema version belongs to a different owner"))
            .whenever(fixture.schemaCompatibility).requireUsable(fixture.definition, fixture.version.schemaVersionId)

        assertThrows<InformationRequestTemplateValidationException> {
            fixture.freezer.freeze(fixture.definition, fixture.version, actor)
        }

        assertUnchanged(fixture)
    }

    private fun assertUnchanged(fixture: TemplateVersionFreezerFixture) {
        verify(fixture.capabilityRepository, never()).save(any())
        verify(fixture.versionRepository, never()).update(any())
        verify(fixture.definitionRepository, never()).update(any())
        assertEquals(InformationRequestTemplateStatus.DRAFT, fixture.version.status)
        assertNull(fixture.version.publishedAt)
    }

    private fun fixture(
        derived: List<InformationRequestCapability>,
        projected: InformationRequestTemplateVersionDto = projection(emptyList()),
    ): TemplateVersionFreezerFixture {
        val definition = InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
            scopeOrgId = UUID.randomUUID()
            namespace = "process"
            templateKey = "collection-pattern"
            displayName = "Collection pattern"
        }
        val version = InformationRequestTemplateVersion().apply {
            templateDefinitionId = definition.id
            versionNumber = 3
        }
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        val capabilityRepository = mock<InformationRequestTemplateVersionCapabilityRepository>()
        whenever(capabilityRepository.findRequiredCapabilities(version.id)).thenReturn(derived)
        val schemaCompatibility = mock<InformationRequestTemplateSchemaCompatibility>()
        val projectionLoader = mock<InformationRequestTemplateProjectionLoader>()
        whenever(projectionLoader.loadVersion(version)).thenReturn(projected)

        return TemplateVersionFreezerFixture(
            freezer = InformationRequestTemplateVersionFreezer(
                definitionRepository,
                versionRepository,
                capabilityRepository,
                schemaCompatibility,
                projectionLoader,
                mock(),
                mock(),
            ),
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            capabilityRepository = capabilityRepository,
            schemaCompatibility = schemaCompatibility,
            definition = definition,
            version = version,
            derived = derived,
        )
    }

    private fun projection(sections: List<InformationRequestTemplateSectionDto>) = InformationRequestTemplateVersionDto(
        id = UUID.randomUUID(),
        templateDefinitionId = UUID.randomUUID(),
        versionNumber = 3,
        status = InformationRequestTemplateStatus.DRAFT,
        schemaVersionId = UUID.randomUUID(),
        sections = sections,
        createdAt = Timestamp.from(Instant.parse("2026-09-27T00:00:00Z")),
    )

    private fun section(key: String, vararg requirements: InformationRequestTemplateRequirementDto) =
        InformationRequestTemplateSectionDto(
            id = UUID.randomUUID(),
            sectionKey = key,
            title = "Section $key",
            requirements = requirements.toList(),
        )

    private fun requirement(key: String, type: InformationRequestRequirementType) =
        InformationRequestTemplateRequirementDto(
            id = UUID.randomUUID(),
            templateRequirementId = UUID.randomUUID(),
            requirementKey = key,
            requirementType = type,
            prompt = "Provide $key",
            responseMode = InformationRequestResponseMode.PROVIDE,
            requiredness = InformationRequestRequiredness.REQUIRED,
            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
            reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
        )
}
