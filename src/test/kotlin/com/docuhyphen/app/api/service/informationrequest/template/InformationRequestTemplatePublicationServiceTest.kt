package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateMutationContext
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
    fun `publication names the requirement a frozen version could not be judged by`()
    {
        val undocumented = requirement("supporting-file", InformationRequestRequirementType.DOCUMENT)
        val fixture = fixture(
            derived = listOf(InformationRequestCapability.DOCUMENT_EVIDENCE),
            draft = draftVersion(sections = listOf(section("evidence", undocumented))),
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.publishTemplate(fixture.definition.id)
        }

        assertEquals("evidence", refusal.sectionKey)
        assertEquals("supporting-file", refusal.requirementKey)
        assertEquals(
            "Requirement supporting-file asks for a document but states no evidence policy to judge its files by",
            refusal.message,
        )
        verify(fixture.capabilityRepository, never()).save(any())
        verify(fixture.versionRepository, never()).update(any())
    }

    @Test
    fun `publication refuses typed data without the schema version it resolves against`()
    {
        val typed = requirement("recorded-note", InformationRequestRequirementType.FIELD)
        val fixture = fixture(
            derived = listOf(InformationRequestCapability.STRUCTURED_RESPONSE),
            draft = draftVersion(sections = listOf(section("collected-data", typed)), schemaVersionId = null),
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            fixture.service.publishTemplate(fixture.definition.id)
        }

        assertEquals("recorded-note", refusal.requirementKey)
        assertEquals(
            "Requirement recorded-note asks for typed data, so this version names the Schema Version it resolves against",
            refusal.message,
        )
        verify(fixture.versionRepository, never()).update(any())
    }

    @Test
    fun `publication refuses a waiver rule and a permitted waived answer that disagree`()
    {
        val waivable = requirement("supporting-file", InformationRequestRequirementType.DOCUMENT).copy(
            evidencePolicy = evidencePolicy(InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED),
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
        )
        val unreachable = requirement("other-file", InformationRequestRequirementType.DOCUMENT).copy(
            evidencePolicy = evidencePolicy(InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED),
            permittedDispositions = listOf(
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.WAIVED,
            ),
        )

        val ruleWithoutAnswer = assertThrows<InformationRequestTemplateValidationException> {
            fixture(
                derived = listOf(InformationRequestCapability.DOCUMENT_EVIDENCE),
                draft = draftVersion(sections = listOf(section("evidence", waivable))),
            ).let { it.service.publishTemplate(it.definition.id) }
        }
        val answerWithoutRule = assertThrows<InformationRequestTemplateValidationException> {
            fixture(
                derived = listOf(InformationRequestCapability.DOCUMENT_EVIDENCE),
                draft = draftVersion(sections = listOf(section("evidence", unreachable))),
            ).let { it.service.publishTemplate(it.definition.id) }
        }

        assertEquals("supporting-file", ruleWithoutAnswer.requirementKey)
        assertEquals(
            "Requirement supporting-file states a waiver rule, so it permits a waived answer",
            ruleWithoutAnswer.message,
        )
        assertEquals("other-file", answerWithoutRule.requirementKey)
        assertEquals(
            "Requirement other-file permits a waived answer, so its evidence policy states the waiver rule that reaches it",
            answerWithoutRule.message,
        )
    }

    @Test
    fun `a ready draft still publishes`()
    {
        val judged = requirement("supporting-file", InformationRequestRequirementType.DOCUMENT).copy(
            evidencePolicy = evidencePolicy(InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED),
        )
        val fixture = fixture(
            derived = listOf(InformationRequestCapability.DOCUMENT_EVIDENCE),
            draft = draftVersion(sections = listOf(section("evidence", judged)), schemaVersionId = null),
        )

        fixture.service.publishTemplate(fixture.definition.id)

        assertEquals(InformationRequestTemplateStatus.PUBLISHED, fixture.version.status)
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
        draft: InformationRequestTemplateVersionDto? = null,
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
        whenever(projected.draftVersion).thenReturn(draft)
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

    private fun draftVersion(
        sections: List<InformationRequestTemplateSectionDto>,
        schemaVersionId: UUID? = UUID.randomUUID(),
    ) = InformationRequestTemplateVersionDto(
        id = UUID.randomUUID(),
        templateDefinitionId = UUID.randomUUID(),
        versionNumber = 1,
        status = InformationRequestTemplateStatus.DRAFT,
        schemaVersionId = schemaVersionId,
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
            collectedFieldDefinitionId = UUID.randomUUID().takeIf { type == InformationRequestRequirementType.FIELD },
            permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
        )

    private fun evidencePolicy(waiver: InformationRequestEvidenceWaiverPolicy) =
        InformationRequestTemplateEvidencePolicyDto(
            id = UUID.randomUUID(),
            minimumFileCount = 1,
            issuerRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            jurisdictionRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            languageRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            issueDateRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            expiryDateRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            coveragePeriodRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            certificationRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            signatureRequirement = InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
            coverageContinuityRequired = false,
            waiverPolicy = waiver,
            conformancePolicy = InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
        )
}
