package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateSection
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapability
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateCapabilityGate
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

private class RequestTemplatePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<RequestTemplatePostgreSQLContainer>(imageName)

class RequestTemplatePostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = RequestTemplatePostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_request_template_persistence_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

/**
 * Reusable request configuration has to survive the round trip through the mapped entities, and
 * every lookup has to answer for the exact identity it was asked about.
 *
 * A key lookup that asks only about a scope kind and an organization answers for every person at
 * once, because they all share the absent organization. A version, section, requirement, or binding
 * lookup that ignores its parent answers for every Template in the deployment. Both mistakes hand
 * one owner's configuration to another, and neither is visible until it happens.
 */
@QuarkusTest
@QuarkusTestResource(RequestTemplatePostgreSQLResource::class)
class InformationRequestTemplatePersistenceContractTest
{
    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var sectionRepository: InformationRequestTemplateSectionRepository

    @Inject
    lateinit var requirementRepository: InformationRequestTemplateRequirementRepository

    @Inject
    lateinit var bindingRepository: InformationRequestTemplateRequirementBindingRepository

    @Inject
    lateinit var dispositionRepository: InformationRequestTemplateBindingDispositionRepository

    @Inject
    lateinit var evidenceLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository

    @Inject
    lateinit var evidencePolicyRepository: InformationRequestTemplateEvidencePolicyRepository

    @Inject
    lateinit var acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository

    @Inject
    lateinit var substituteRepository: InformationRequestTemplateBindingSubstituteRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var capabilityGate: InformationRequestTemplateCapabilityGate

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `a template definition, version, section, requirement, and binding survive the round trip`()
    {
        val owner = insertOwner("round-trip-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }
        val section = QuarkusTransaction.requiringNew().call {
            sectionRepository.save(section(version.id, "collected-data", 1))
        }
        val requirement = QuarkusTransaction.requiringNew().call {
            requirementRepository.save(requirement(definition.id, "recorded-note"))
        }
        val binding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(
                binding(version.id, definition.id, requirement.id, section.id, 1, collectableField()),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val storedDefinition = definitionRepository.findById(definition.id)
            assertEquals(owner, storedDefinition?.scopeUserId)
            assertNull(storedDefinition?.scopeOrgId)
            assertEquals(InformationRequestTemplateScopeKind.PERSONAL, storedDefinition?.scopeKind)
            assertEquals(InformationRequestTemplateStatus.DRAFT, versionRepository.findById(version.id)?.status)
            assertEquals("collected-data", sectionRepository.findById(section.id)?.sectionKey)
            assertEquals("recorded-note", requirementRepository.findById(requirement.id)?.requirementKey)
            assertEquals(requirement.id, bindingRepository.findById(binding.id)?.templateRequirementId)
            assertEquals(section.id, bindingRepository.findById(binding.id)?.templateSectionId)
        }
    }

    @Test
    fun `a template key lookup answers for one owner, not for everybody without an organization`()
    {
        val owner = insertOwner("first-template-owner")
        val otherOwner = insertOwner("second-template-owner")
        val namespace = uniqueNamespace()

        val owned = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace)).id
        }
        val otherOwned = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(otherOwner, namespace)).id
        }
        val platformOwned = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(platformDefinition(namespace)).id
        }

        assertNotEquals(owned, otherOwned, "Both people hold the same key under their own name")

        QuarkusTransaction.requiringNew().run {
            assertEquals(owned, findPersonalByKey(owner, namespace)?.id)
            assertEquals(otherOwned, findPersonalByKey(otherOwner, namespace)?.id)
            assertEquals(
                platformOwned,
                definitionRepository.findByKey(
                    InformationRequestTemplateScopeKind.PLATFORM, null, null, namespace, TEMPLATE_KEY,
                )?.id,
            )
            assertNull(
                findPersonalByKey(insertOwner("third-template-owner"), namespace),
                "A third person has not taken the key merely by having no organization either",
            )
            assertEquals(listOf(owned), definitionRepository.findAllForUser(owner).map { it.id })
        }
    }

    @Test
    fun `a version, section, requirement, and binding lookup answers only for its own parent`()
    {
        val owner = insertOwner("parent-scope-owner")
        val otherOwner = insertOwner("other-parent-scope-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val otherDefinition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(otherOwner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }
        val otherVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(otherDefinition.id, 1))
        }

        QuarkusTransaction.requiringNew().run {
            sectionRepository.save(section(version.id, "collected-data", 1))
            sectionRepository.save(section(otherVersion.id, "collected-data", 1))
            requirementRepository.save(requirement(definition.id, "recorded-note"))
            requirementRepository.save(requirement(otherDefinition.id, "recorded-note"))
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(version.id, versionRepository.findByNumber(definition.id, 1)?.id)
            assertEquals(otherVersion.id, versionRepository.findByNumber(otherDefinition.id, 1)?.id)
            assertEquals(
                listOf(version.id),
                sectionRepository.findOrdered(version.id).map { it.templateVersionId },
            )
            assertEquals(
                listOf(definition.id),
                requirementRepository.findAllByDefinition(definition.id).map { it.templateDefinitionId },
            )
            assertEquals(
                definition.id,
                requirementRepository.findByKey(definition.id, "recorded-note")?.templateDefinitionId,
            )
            assertNull(versionRepository.findByNumber(definition.id, 2))
        }
    }

    @Test
    fun `a version reads its sections and bindings in the order it states`()
    {
        val owner = insertOwner("ordering-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }

        val sections = QuarkusTransaction.requiringNew().call {
            val second = sectionRepository.save(section(version.id, "supporting-data", 2))
            val first = sectionRepository.save(section(version.id, "collected-data", 1))
            listOf(first.id, second.id)
        }
        QuarkusTransaction.requiringNew().run {
            val second = requirementRepository.save(requirement(definition.id, "supporting-note"))
            val first = requirementRepository.save(requirement(definition.id, "recorded-note"))
            bindingRepository.save(binding(version.id, definition.id, second.id, sections[0], 2, collectableField()))
            bindingRepository.save(binding(version.id, definition.id, first.id, sections[0], 1, collectableField()))
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf("collected-data", "supporting-data"),
                sectionRepository.findOrdered(version.id).map { it.sectionKey },
            )
            assertEquals(
                listOf(1, 2),
                bindingRepository.findOrdered(version.id).map { it.displayOrder },
            )
        }
    }

    @Test
    fun `a published version is found for its definition and a draft is not`()
    {
        val owner = insertOwner("publication-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val firstPublished = QuarkusTransaction.requiringNew().call {
            versionRepository.save(publishedVersion(definition.id, 1, owner)).id
        }
        val secondPublished = QuarkusTransaction.requiringNew().call {
            versionRepository.save(publishedVersion(definition.id, 2, owner)).id
        }
        val stillDraft = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 3)).id
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf(firstPublished, secondPublished),
                versionRepository.findPublished(definition.id).map { it.id },
            )
            assertEquals(secondPublished, versionRepository.findLatestPublished(definition.id)?.id)
            assertNotEquals(stillDraft, versionRepository.findLatestPublished(definition.id)?.id)
        }
    }

    @Test
    fun `a requirement type and the respondent policy of its binding survive the round trip`()
    {
        val owner = insertOwner("policy-round-trip-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }
        val section = QuarkusTransaction.requiringNew().call {
            sectionRepository.save(section(version.id, "collected-data", 1))
        }
        val requirement = QuarkusTransaction.requiringNew().call {
            requirementRepository.save(
                requirement(definition.id, "recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION),
            )
        }
        val binding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(
                binding(version.id, definition.id, requirement.id, section.id, 1).apply {
                    helpText = "Use the recorded wording"
                    responseMode = InformationRequestResponseMode.PROVIDE_ONCE
                    requiredness = InformationRequestRequiredness.CONDITIONAL
                    contributorRole = InformationRequestContributorRole.ATTESTOR
                    reviewPolicy = InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION
                    confidentialityCompartmentKey = "restricted-review"
                    conditionalRuleKey = "prior-answer-recorded"
                    occurrenceAnchorKey = "repeated-entry"
                },
            )
        }

        QuarkusTransaction.requiringNew().run {
            val storedRequirement = requirementRepository.findById(requirement.id)
            assertEquals(
                InformationRequestRequirementType.RESPONSE_ATTESTATION,
                storedRequirement?.requirementType,
            )

            val storedBinding = bindingRepository.findById(binding.id)
            assertEquals("State the recorded note", storedBinding?.prompt)
            assertEquals("Use the recorded wording", storedBinding?.helpText)
            assertEquals(InformationRequestResponseMode.PROVIDE_ONCE, storedBinding?.responseMode)
            assertEquals(InformationRequestRequiredness.CONDITIONAL, storedBinding?.requiredness)
            assertEquals(InformationRequestContributorRole.ATTESTOR, storedBinding?.contributorRole)
            assertEquals(InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION, storedBinding?.reviewPolicy)
            assertEquals("restricted-review", storedBinding?.confidentialityCompartmentKey)
            assertEquals("prior-answer-recorded", storedBinding?.conditionalRuleKey)
            assertEquals("repeated-entry", storedBinding?.occurrenceAnchorKey)

            assertEquals(
                listOf(requirement.id),
                requirementRepository.findAllByType(
                    definition.id, InformationRequestRequirementType.RESPONSE_ATTESTATION,
                ).map { it.id },
            )
            assertEquals(
                emptyList<UUID>(),
                requirementRepository.findAllByType(definition.id, InformationRequestRequirementType.FIELD)
                    .map { it.id },
            )
        }
    }

    @Test
    fun `permitted answers and supporting evidence answer only for their own binding and version`()
    {
        val owner = insertOwner("policy-set-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }
        val otherVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 2))
        }
        val section = QuarkusTransaction.requiringNew().call {
            sectionRepository.save(section(version.id, "collected-data", 1))
        }
        val otherSection = QuarkusTransaction.requiringNew().call {
            sectionRepository.save(section(otherVersion.id, "collected-data", 1))
        }

        val answered = QuarkusTransaction.requiringNew().call {
            requirementRepository.save(requirement(definition.id, "recorded-note"))
        }
        val requested = QuarkusTransaction.requiringNew().call {
            requirementRepository.save(
                requirement(definition.id, "supporting-record", InformationRequestRequirementType.DOCUMENT),
            )
        }

        val collectedField = collectableField()
        val answeredBinding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(binding(version.id, definition.id, answered.id, section.id, 1, collectedField))
        }
        val requestedBinding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(binding(version.id, definition.id, requested.id, section.id, 2))
        }
        // Another version asks its own question, so collecting the same field there is not the same
        // answer being recorded twice.
        val otherVersionBinding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(
                binding(otherVersion.id, definition.id, answered.id, otherSection.id, 1, collectedField),
            )
        }

        QuarkusTransaction.requiringNew().run {
            dispositionRepository.save(
                disposition(answeredBinding.id, version.id, InformationRequestResponseDisposition.PROVIDED),
            )
            dispositionRepository.save(
                disposition(answeredBinding.id, version.id, InformationRequestResponseDisposition.UNAVAILABLE),
            )
            dispositionRepository.save(
                disposition(otherVersionBinding.id, otherVersion.id, InformationRequestResponseDisposition.PROVIDED),
            )
            evidenceLinkRepository.save(evidenceLink(answeredBinding.id, requestedBinding.id, version.id))
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                setOf(
                    InformationRequestResponseDisposition.PROVIDED,
                    InformationRequestResponseDisposition.UNAVAILABLE,
                ),
                dispositionRepository.findForBinding(answeredBinding.id).map { it.disposition }.toSet(),
            )
            assertEquals(
                emptyList<UUID>(),
                dispositionRepository.findForBinding(requestedBinding.id).map { it.id },
            )
            assertEquals(2, dispositionRepository.findForVersion(version.id).size)
            assertEquals(1, dispositionRepository.findForVersion(otherVersion.id).size)

            assertEquals(
                listOf(requestedBinding.id),
                evidenceLinkRepository.findForBinding(answeredBinding.id).map { it.supportingTemplateBindingId },
            )
            assertEquals(
                listOf(answeredBinding.id),
                evidenceLinkRepository.findSupportedBy(requestedBinding.id).map { it.templateBindingId },
            )
            assertEquals(
                emptyList<UUID>(),
                evidenceLinkRepository.findSupportedBy(answeredBinding.id).map { it.id },
            )
            assertEquals(1, evidenceLinkRepository.findForVersion(version.id).size)
            assertEquals(0, evidenceLinkRepository.findForVersion(otherVersion.id).size)
        }
    }

    @Test
    fun `an evidence policy and its sets answer only for their own binding, policy, and version`()
    {
        val owner = insertOwner("evidence-policy-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }
        val otherVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 2))
        }
        val section = QuarkusTransaction.requiringNew().call {
            sectionRepository.save(section(version.id, "collected-data", 1))
        }
        val otherSection = QuarkusTransaction.requiringNew().call {
            sectionRepository.save(section(otherVersion.id, "collected-data", 1))
        }

        val requested = QuarkusTransaction.requiringNew().call {
            requirementRepository.save(
                requirement(definition.id, "supporting-record", InformationRequestRequirementType.DOCUMENT),
            )
        }
        val alternate = QuarkusTransaction.requiringNew().call {
            requirementRepository.save(
                requirement(definition.id, "alternate-record", InformationRequestRequirementType.DOCUMENT),
            )
        }

        val requestedBinding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(binding(version.id, definition.id, requested.id, section.id, 1))
        }
        val alternateBinding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(binding(version.id, definition.id, alternate.id, section.id, 2))
        }
        val otherVersionBinding = QuarkusTransaction.requiringNew().call {
            bindingRepository.save(binding(otherVersion.id, definition.id, requested.id, otherSection.id, 1))
        }

        val policy = QuarkusTransaction.requiringNew().call {
            evidencePolicyRepository.save(strictPolicy(requestedBinding.id, version.id))
        }
        val alternatePolicy = QuarkusTransaction.requiringNew().call {
            evidencePolicyRepository.save(permissivePolicy(alternateBinding.id, version.id))
        }
        QuarkusTransaction.requiringNew().run {
            evidencePolicyRepository.save(permissivePolicy(otherVersionBinding.id, otherVersion.id))
            acceptedValueRepository.save(
                acceptedValue(
                    policy.id, version.id, InformationRequestEvidenceAttribute.CONTENT_TYPE, "application/pdf",
                ),
            )
            acceptedValueRepository.save(
                acceptedValue(
                    policy.id, version.id, InformationRequestEvidenceAttribute.CONTENT_TYPE, "image/png",
                ),
            )
            acceptedValueRepository.save(
                acceptedValue(policy.id, version.id, InformationRequestEvidenceAttribute.LANGUAGE, "en"),
            )
            substituteRepository.save(substitute(requestedBinding.id, alternateBinding.id, version.id))
        }

        QuarkusTransaction.requiringNew().run {
            val stored = evidencePolicyRepository.findForBinding(requestedBinding.id)
            assertEquals(2, stored?.minimumFileCount)
            assertEquals(6, stored?.maximumFileCount)
            assertEquals(1_000_000L, stored?.maximumFileSizeBytes)
            assertEquals(4_000_000L, stored?.maximumTotalSizeBytes)
            assertEquals(1, stored?.minimumPageCount)
            assertEquals(40, stored?.maximumPageCount)
            assertEquals(
                InformationRequestEvidenceAttributeRequirement.REQUIRED,
                stored?.issuerRequirement,
            )
            assertEquals(
                InformationRequestEvidenceAttributeRequirement.OPTIONAL,
                stored?.jurisdictionRequirement,
            )
            assertEquals(
                InformationRequestEvidenceAttributeRequirement.REQUIRED,
                stored?.certificationRequirement,
            )
            assertEquals(
                InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                stored?.signatureRequirement,
            )
            assertEquals(90, stored?.maximumIssueAgeDays)
            assertEquals(0, stored?.minimumRemainingValidityDays)
            assertEquals(90, stored?.minimumCoverageDays)
            assertEquals(true, stored?.coverageContinuityRequired)
            assertEquals(
                InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED,
                stored?.waiverPolicy,
            )
            assertEquals(
                InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE,
                stored?.conformancePolicy,
            )
            assertEquals(alternatePolicy.id, evidencePolicyRepository.findForBinding(alternateBinding.id)?.id)
            assertEquals(2, evidencePolicyRepository.findForVersion(version.id).size)
            assertEquals(1, evidencePolicyRepository.findForVersion(otherVersion.id).size)

            assertEquals(3, acceptedValueRepository.findForPolicy(policy.id).size)
            assertEquals(
                listOf("application/pdf", "image/png"),
                acceptedValueRepository
                    .findForPolicyAttribute(policy.id, InformationRequestEvidenceAttribute.CONTENT_TYPE)
                    .map { it.acceptedValue },
            )
            assertEquals(
                emptyList<UUID>(),
                acceptedValueRepository.findForPolicy(alternatePolicy.id).map { it.id },
            )
            assertEquals(3, acceptedValueRepository.findForVersion(version.id).size)
            assertEquals(0, acceptedValueRepository.findForVersion(otherVersion.id).size)

            assertEquals(
                listOf(alternateBinding.id),
                substituteRepository.findForBinding(requestedBinding.id).map { it.substituteTemplateBindingId },
            )
            assertEquals(
                listOf(requestedBinding.id),
                substituteRepository.findSubstitutedBy(alternateBinding.id).map { it.templateBindingId },
            )
            assertEquals(
                emptyList<UUID>(),
                substituteRepository.findForBinding(alternateBinding.id).map { it.id },
            )
            assertEquals(1, substituteRepository.findForVersion(version.id).size)
            assertEquals(0, substituteRepository.findForVersion(otherVersion.id).size)
        }
    }

    @Test
    fun `a version records what it needs a runtime to supply and issuance refuses what is missing`()
    {
        val owner = insertOwner("capability-owner")
        val namespace = uniqueNamespace()

        val definition = QuarkusTransaction.requiringNew().call {
            definitionRepository.save(personalDefinition(owner, namespace))
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 1))
        }
        val otherVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(draftVersion(definition.id, 2))
        }

        QuarkusTransaction.requiringNew().run {
            capabilityRepository.save(
                requiredCapability(version.id, InformationRequestCapability.RESPONSE_ATTESTATION, 1),
            )
            capabilityRepository.save(
                requiredCapability(version.id, InformationRequestCapability.RESPONSE_SUBMISSION, 1),
            )
            capabilityRepository.save(
                requiredCapability(otherVersion.id, InformationRequestCapability.DOCUMENT_EVIDENCE, 1),
            )
        }

        QuarkusTransaction.requiringNew().run {
            // The set belongs to the version that recorded it, so a lookup that ignored its parent
            // would issue one version against another version's runtime.
            assertEquals(
                listOf(
                    InformationRequestCapability.RESPONSE_ATTESTATION,
                    InformationRequestCapability.RESPONSE_SUBMISSION,
                ),
                capabilityRepository.findForVersion(version.id).map { it.capabilityKey },
            )
            assertEquals(
                listOf(InformationRequestCapability.DOCUMENT_EVIDENCE),
                capabilityRepository.findForVersion(otherVersion.id).map { it.capabilityKey },
            )
            assertEquals(
                listOf(1, 1),
                capabilityRepository.findForVersion(version.id).map { it.requiredContractVersion },
            )

            assertEquals(
                listOf(
                    InformationRequestCapabilityRequirement(
                        InformationRequestCapability.RESPONSE_ATTESTATION, 1,
                    ),
                    InformationRequestCapabilityRequirement(
                        InformationRequestCapability.RESPONSE_SUBMISSION, 1,
                    ),
                ),
                capabilityGate.requirementsOf(version.id),
            )

            // No executor is installed while the runtime is built, so the version is published and
            // valid but cannot be issued, and the refusal names every capability that is missing
            // rather than the first one found.
            assertEquals(
                capabilityGate.requirementsOf(version.id),
                capabilityGate.unservedRequirements(version.id),
            )
            val refusal = assertThrows<InformationRequestCapabilityNotInstalledException> {
                capabilityGate.requireInstalledCapabilities(version.id)
            }
            assertEquals(capabilityGate.requirementsOf(version.id), refusal.unserved)
            assertTrue(
                refusal.message.contains("RESPONSE_ATTESTATION v1") &&
                    refusal.message.contains("RESPONSE_SUBMISSION v1"),
                "The refusal should name every unserved capability: ${refusal.message}",
            )

            // A version that recorded nothing needs nothing, so nothing stands in the way of it.
            val unrecorded = QuarkusTransaction.requiringNew().call {
                versionRepository.save(draftVersion(definition.id, 3))
            }
            assertEquals(emptyList<InformationRequestCapabilityRequirement>(),
                capabilityGate.unservedRequirements(unrecorded.id))
            capabilityGate.requireInstalledCapabilities(unrecorded.id)
        }
    }

    private fun requiredCapability(
        versionId: UUID,
        capability: InformationRequestCapability,
        contractVersion: Int,
    ) = InformationRequestTemplateVersionCapability().apply {
        templateVersionId = versionId
        capabilityKey = capability
        requiredContractVersion = contractVersion
    }
    private fun findPersonalByKey(owner: UUID, namespace: String): InformationRequestTemplateDefinition? =
        definitionRepository.findByKey(
            InformationRequestTemplateScopeKind.PERSONAL, null, owner, namespace, TEMPLATE_KEY,
        )

    private fun personalDefinition(owner: UUID, namespace: String) =
        InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL
            scopeUserId = owner
            this.namespace = namespace
            templateKey = TEMPLATE_KEY
            displayName = "Collection pattern"
        }

    private fun platformDefinition(namespace: String) =
        InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PLATFORM
            this.namespace = namespace
            templateKey = TEMPLATE_KEY
            displayName = "Collection pattern"
        }

    private fun draftVersion(definitionId: UUID, number: Int) =
        InformationRequestTemplateVersion().apply {
            templateDefinitionId = definitionId
            versionNumber = number
        }

    private fun publishedVersion(definitionId: UUID, number: Int, actorId: UUID) =
        InformationRequestTemplateVersion().apply {
            templateDefinitionId = definitionId
            versionNumber = number
            status = InformationRequestTemplateStatus.PUBLISHED
            publishedAt = Timestamp.from(Instant.now())
            publishedByAppUserId = actorId
        }

    private fun section(versionId: UUID, key: String, order: Int) =
        InformationRequestTemplateSection().apply {
            templateVersionId = versionId
            sectionKey = key
            displayOrder = order
            title = "Collected data"
        }

    private fun requirement(
        definitionId: UUID,
        key: String,
        type: InformationRequestRequirementType = InformationRequestRequirementType.FIELD,
    ) = InformationRequestTemplateRequirement().apply {
        templateDefinitionId = definitionId
        requirementKey = key
        requirementType = type
    }

    private fun binding(
        versionId: UUID,
        definitionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        order: Int,
        collectedField: UUID? = null,
    ) = InformationRequestTemplateRequirementBinding().apply {
        templateVersionId = versionId
        templateDefinitionId = definitionId
        templateRequirementId = requirementId
        templateSectionId = sectionId
        displayOrder = order
        prompt = "State the recorded note"
        collectedFieldDefinitionId = collectedField
    }

    /**
     * One stable field a typed requirement can collect. Each call creates its own, because one
     * version collects one field once.
     */
    private fun collectableField(): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO field_definition
                    (id, scope_kind, namespace, field_key, status, created_at, updated_at)
                VALUES (?, 'PLATFORM', 'process', ?, 'PUBLISHED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setObject(1, id)
                statement.setString(2, "recorded-note-${id.toString().take(8)}")
                statement.setTimestamp(3, now)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        }
        return id
    }

    private fun disposition(
        bindingId: UUID,
        versionId: UUID,
        value: InformationRequestResponseDisposition,
    ) = InformationRequestTemplateBindingDisposition().apply {
        templateBindingId = bindingId
        templateVersionId = versionId
        disposition = value
    }

    private fun evidenceLink(bindingId: UUID, supportingBindingId: UUID, versionId: UUID) =
        InformationRequestTemplateBindingEvidenceLink().apply {
            templateBindingId = bindingId
            supportingTemplateBindingId = supportingBindingId
            templateVersionId = versionId
        }

    /** Every bound and every captured attribute set, so a mismapped column cannot pass unnoticed. */
    private fun strictPolicy(bindingId: UUID, versionId: UUID) =
        InformationRequestTemplateEvidencePolicy().apply {
            templateBindingId = bindingId
            templateVersionId = versionId
            minimumFileCount = 2
            maximumFileCount = 6
            maximumFileSizeBytes = 1_000_000
            maximumTotalSizeBytes = 4_000_000
            minimumPageCount = 1
            maximumPageCount = 40
            issuerRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED
            jurisdictionRequirement = InformationRequestEvidenceAttributeRequirement.OPTIONAL
            languageRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED
            issueDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED
            expiryDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED
            coveragePeriodRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED
            certificationRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED
            maximumIssueAgeDays = 90
            minimumRemainingValidityDays = 0
            minimumCoverageDays = 90
            coverageContinuityRequired = true
            waiverPolicy = InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED
            conformancePolicy = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE
        }

    /** The least restrictive policy a requested Document can be judged by. */
    private fun permissivePolicy(bindingId: UUID, versionId: UUID) =
        InformationRequestTemplateEvidencePolicy().apply {
            templateBindingId = bindingId
            templateVersionId = versionId
        }

    private fun acceptedValue(
        policyId: UUID,
        versionId: UUID,
        restricted: InformationRequestEvidenceAttribute,
        value: String,
    ) = InformationRequestTemplateEvidenceAcceptedValue().apply {
        evidencePolicyId = policyId
        templateVersionId = versionId
        attribute = restricted
        acceptedValue = value
    }

    private fun substitute(bindingId: UUID, substituteBindingId: UUID, versionId: UUID) =
        InformationRequestTemplateBindingSubstitute().apply {
            templateBindingId = bindingId
            substituteTemplateBindingId = substituteBindingId
            templateVersionId = versionId
        }

    /** Namespaces are per test so one test's stored key never decides another's lookup. */
    private fun uniqueNamespace(): String = "process-${UUID.randomUUID().toString().take(8)}"

    private fun insertOwner(label: String): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, "$label-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }
        return id
    }

    private companion object
    {
        const val TEMPLATE_KEY = "collection-pattern"
    }
}
