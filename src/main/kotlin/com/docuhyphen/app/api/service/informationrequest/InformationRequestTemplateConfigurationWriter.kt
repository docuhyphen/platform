package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicate
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicateLiteral
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionRule
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateSection
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateConditionPredicateLiteralRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateConditionPredicateRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateConditionRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import java.util.UUID

/**
 * Writes one authored configuration document into one draft Template Version.
 *
 * A draft is replaced whole rather than patched. The parts of a configuration are spread over eight
 * tables joined by composite keys, with one-way relations and ordering constraints between them, so
 * a partial edit has no coherent meaning: a section removed while its requirements remain, or a
 * substitute kept after the document it stood in for is gone, would leave a version that renders as
 * something no author ever stated. Replacing the whole document means the stored configuration is
 * always exactly one authored statement.
 *
 * Positions are never authored. A section takes the position it is listed at and a requirement takes
 * the position it is listed at within its section, so the stored order cannot contain a gap, a
 * duplicate, or a disagreement with what the author saw.
 *
 * Requirement identities are not part of the replaced document. They belong to the definition rather
 * than the version, so a rewrite reuses the identity behind a key it still names, creates one for a
 * key it names for the first time, and leaves behind the identity of a key it no longer names, which
 * a later version can ask about again.
 */
@ApplicationScoped
class InformationRequestTemplateConfigurationWriter @Inject constructor(
    private val validator: InformationRequestTemplateConfigurationValidator,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val sectionRepository: InformationRequestTemplateSectionRepository,
    private val requirementRepository: InformationRequestTemplateRequirementRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val evidencePolicyRepository: InformationRequestTemplateEvidencePolicyRepository,
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository,
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository,
    private val evidenceLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository,
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val conditionRuleRepository: InformationRequestTemplateConditionRuleRepository,
    private val conditionPredicateRepository: InformationRequestTemplateConditionPredicateRepository,
    private val conditionPredicateLiteralRepository: InformationRequestTemplateConditionPredicateLiteralRepository,
    private val attestationPolicyWriter: InformationRequestTemplateAttestationPolicyWriter,
)
{
    /**
     * The stored rules that decide whether a relation is permitted read the rows already written
     * rather than the rows still pending, so each stage of the write reaches the database before the
     * stage that depends on it is attempted. That ordering is the reason this class controls the
     * flush rather than leaving it to the end of the transaction.
     */
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    /**
     * Replaces everything [draft] configures with [request].
     *
     * @throws InformationRequestTemplateValidationException when the document does not hold
     * together, or when it asks a requirement to change what kind of thing it asks for.
     */
    @Transactional
    fun replaceConfiguration(
        draft: InformationRequestTemplateVersion,
        request: InformationRequestTemplateConfigurationRequest,
    )
    {
        val document = validator.normalize(request)
        val authored = document.sections.flatMap { it.requirements }

        clearConfiguration(draft.id)
        writeGroups(draft.id, document.groups)
        writeConditionRules(draft.id, document.conditionRules)
        val requirements = resolveRequirements(draft.templateDefinitionId, authored)
        val bindings = writeStructure(draft, document, requirements)
        writeBindingPolicies(draft.id, authored, bindings)
        attestationPolicyWriter.write(draft.id, authored, bindings)

        draft.schemaVersionId = document.schemaVersionId
        draft.submissionMode = document.submissionMode
        draft.submissionStageOrdering = document.submissionStageOrdering
        versionRepository.update(draft)
    }

    // ── Removing what the previous document stated ─────────────────────────────

    /**
     * Children before parents, because every one of these rows is reachable from the row above it
     * and the database refuses to leave a reference behind. The stored freeze rule refuses each of
     * these statements against a version that is no longer a draft, so this is also what stops a
     * frozen configuration from being emptied.
     */
    private fun clearConfiguration(templateVersionId: UUID)
    {
        attestationPolicyWriter.clear(templateVersionId)
        acceptedValueRepository.deleteForVersion(templateVersionId)
        evidencePolicyRepository.deleteForVersion(templateVersionId)
        substituteRepository.deleteForVersion(templateVersionId)
        evidenceLinkRepository.deleteForVersion(templateVersionId)
        dispositionRepository.deleteForVersion(templateVersionId)
        bindingRepository.deleteForVersion(templateVersionId)
        sectionRepository.deleteForVersion(templateVersionId)
        groupRepository.deleteForVersion(templateVersionId)
        conditionPredicateLiteralRepository.deleteForVersion(templateVersionId)
        conditionPredicateRepository.deleteForVersion(templateVersionId)
        conditionRuleRepository.deleteForVersion(templateVersionId)
        entityManager.flush()
    }

    // ── The repeatable and nested groups one version defines ───────────────────

    /**
     * Groups nest by key rather than by row, so a child has to be written after the parent it names
     * exists to store. The document is authored in any order, so this writes whichever groups are
     * ready -- having no parent, or a parent already written -- one layer at a time until none
     * remain. The validator already proved the document is acyclic, so every layer is non-empty.
     */
    private fun writeGroups(
        templateVersionId: UUID,
        groups: List<InformationRequestTemplateGroupRequest>,
    )
    {
        val writtenIdByKey = mutableMapOf<String, UUID>()
        var remaining = groups
        while (remaining.isNotEmpty())
        {
            val (ready, notReady) = remaining.partition {
                it.parentGroupKey == null || writtenIdByKey.containsKey(it.parentGroupKey)
            }
            check(ready.isNotEmpty()) {
                "Repeatable group nesting did not resolve, which the validator should have refused first"
            }
            ready.forEach { authored ->
                val saved = groupRepository.save(
                    InformationRequestTemplateRequirementGroup().apply {
                        this.templateVersionId = templateVersionId
                        this.groupKey = authored.groupKey
                        this.parentGroupId = authored.parentGroupKey?.let { writtenIdByKey.getValue(it) }
                        this.minOccurrences = authored.minOccurrences
                        this.maxOccurrences = authored.maxOccurrences
                    },
                )
                writtenIdByKey[authored.groupKey] = saved.id
            }
            remaining = notReady
        }

        // Reaches the database before anything relies on a parent group already existing, the same
        // reason the structure written below is flushed before its own policies are stated.
        entityManager.flush()
    }

    // ── The versioned conditions one version defines ───────────────────────────

    private fun writeConditionRules(
        templateVersionId: UUID,
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
    )
    {
        conditionRules.forEach { authoredRule ->
            val rule = conditionRuleRepository.save(
                InformationRequestTemplateConditionRule().apply {
                    this.templateVersionId = templateVersionId
                    this.ruleKey = authoredRule.ruleKey
                    this.expressionVersion = authoredRule.expressionVersion
                    this.hiddenDataPolicy = authoredRule.hiddenDataPolicy
                },
            )
            authoredRule.predicates.forEachIndexed { predicateIndex, authoredPredicate ->
                writeConditionPredicate(templateVersionId, rule.id, predicateIndex, authoredPredicate)
            }
        }

        // Reaches the database before the requirement bindings written below are checked against it.
        entityManager.flush()
    }

    private fun writeConditionPredicate(
        templateVersionId: UUID,
        conditionRuleId: UUID,
        displayOrder: Int,
        authored: InformationRequestTemplateConditionPredicateRequest,
    )
    {
        val predicate = InformationRequestTemplateConditionPredicate().apply {
            this.conditionRuleId = conditionRuleId
            this.templateVersionId = templateVersionId
            this.displayOrder = displayOrder
            this.sourceRequirementKey = authored.sourceRequirementKey
            this.fieldDefinitionId = authored.fieldDefinitionId
            this.valueType = authored.valueType
            this.operator = authored.operator
            this.expectedDisposition = authored.expectedDisposition
        }
        InformationRequestConditionPredicateLiteralCodec.applyScalarTo(predicate, authored.value)
        val saved = conditionPredicateRepository.save(predicate)

        InformationRequestConditionPredicateLiteralCodec
            .listLiteralValues(authored.operator, authored.value)
            .forEachIndexed { literalIndex, literalValue ->
                conditionPredicateLiteralRepository.save(
                    InformationRequestTemplateConditionPredicateLiteral().apply {
                        this.conditionPredicateId = saved.id
                        this.templateVersionId = templateVersionId
                        this.literalValue = literalValue
                        this.displayOrder = literalIndex
                    },
                )
            }
    }

    // ── The stable identities the document names ───────────────────────────────

    private fun resolveRequirements(
        templateDefinitionId: UUID,
        authored: List<InformationRequestTemplateRequirementRequest>,
    ): Map<String, InformationRequestTemplateRequirement>
    {
        val stored = requirementRepository.findAllByDefinition(templateDefinitionId)
            .associateBy { it.requirementKey }

        return authored.associate { requirement ->
            val existing = stored[requirement.requirementKey]
            requirement.requirementKey to when
            {
                existing == null -> requirementRepository.save(
                    InformationRequestTemplateRequirement().apply {
                        this.templateDefinitionId = templateDefinitionId
                        this.requirementKey = requirement.requirementKey
                        this.requirementType = requirement.requirementType
                    },
                )
                existing.requirementType != requirement.requirementType -> throw
                    InformationRequestTemplateValidationException(
                        "Requirement ${requirement.requirementKey} already asks for " +
                            "${existing.requirementType}, and what a requirement asks for is part of " +
                            "its stable identity rather than something one version restates",
                        requirementKey = requirement.requirementKey,
                    )
                else -> existing
            }
        }
    }

    // ── The structure one version states ──────────────────────────────────────

    /** Returns the binding placed for each requirement key, keyed by that key. */
    private fun writeStructure(
        draft: InformationRequestTemplateVersion,
        document: InformationRequestTemplateConfigurationRequest,
        requirements: Map<String, InformationRequestTemplateRequirement>,
    ): Map<String, InformationRequestTemplateRequirementBinding>
    {
        val placed = mutableMapOf<String, InformationRequestTemplateRequirementBinding>()

        document.sections.forEachIndexed { sectionIndex, authoredSection ->
            val section = sectionRepository.save(
                InformationRequestTemplateSection().apply {
                    templateVersionId = draft.id
                    sectionKey = authoredSection.sectionKey
                    displayOrder = sectionIndex + 1
                    title = authoredSection.title
                    helpText = authoredSection.helpText
                    submissionStageKey = authoredSection.submissionStageKey
                },
            )

            authoredSection.requirements.forEachIndexed { requirementIndex, authoredRequirement ->
                val requirement = requirements.getValue(authoredRequirement.requirementKey)
                placed[authoredRequirement.requirementKey] = bindingRepository.save(
                    InformationRequestTemplateRequirementBinding().apply {
                        templateVersionId = draft.id
                        templateDefinitionId = draft.templateDefinitionId
                        templateRequirementId = requirement.id
                        templateSectionId = section.id
                        displayOrder = requirementIndex + 1
                        prompt = authoredRequirement.prompt
                        helpText = authoredRequirement.helpText
                        responseMode = authoredRequirement.responseMode
                        requiredness = authoredRequirement.requiredness
                        contributorRole = authoredRequirement.contributorRole
                        reviewPolicy = authoredRequirement.reviewPolicy
                        confidentialityCompartmentKey = authoredRequirement.confidentialityCompartmentKey
                        conditionalRuleKey = authoredRequirement.conditionalRuleKey
                        occurrenceAnchorKey = authoredRequirement.occurrenceAnchorKey
                        collectedFieldDefinitionId = authoredRequirement.collectedFieldDefinitionId
                    },
                )
            }
        }

        // Every rule below reads a binding to decide what kind of thing it is being stated about.
        entityManager.flush()
        return placed
    }

    // ── What one version states about each requirement it places ───────────────

    private fun writeBindingPolicies(
        templateVersionId: UUID,
        authored: List<InformationRequestTemplateRequirementRequest>,
        bindings: Map<String, InformationRequestTemplateRequirementBinding>,
    )
    {
        val stated = mutableListOf<
            Pair<InformationRequestTemplateEvidencePolicy, InformationRequestTemplateEvidencePolicyRequest>,
            >()

        authored.forEach { requirement ->
            val binding = bindings.getValue(requirement.requirementKey)

            requirement.permittedDispositions.forEach { permitted ->
                dispositionRepository.save(
                    InformationRequestTemplateBindingDisposition().apply {
                        templateBindingId = binding.id
                        this.templateVersionId = templateVersionId
                        disposition = permitted
                    },
                )
            }

            requirement.substituteRequirementKeys.forEach { substituteKey ->
                substituteRepository.save(
                    InformationRequestTemplateBindingSubstitute().apply {
                        templateBindingId = binding.id
                        substituteTemplateBindingId = bindings.getValue(substituteKey).id
                        this.templateVersionId = templateVersionId
                    },
                )
            }

            requirement.supportingEvidenceRequirementKeys.forEach { supportingKey ->
                evidenceLinkRepository.save(
                    InformationRequestTemplateBindingEvidenceLink().apply {
                        templateBindingId = binding.id
                        supportingTemplateBindingId = bindings.getValue(supportingKey).id
                        this.templateVersionId = templateVersionId
                    },
                )
            }

            requirement.evidencePolicy?.let { authoredPolicy ->
                stated += writeEvidencePolicy(templateVersionId, binding.id, authoredPolicy) to
                    authoredPolicy
            }
        }

        if (stated.isEmpty())
        {
            return
        }

        // An accepted value names the policy it restricts, so every policy reaches the database
        // before any of them is restricted. Doing that once rather than once per document keeps the
        // cost of a write independent of how many documents the version asks for.
        entityManager.flush()
        stated.forEach { (policy, authored) -> writeAcceptedValues(templateVersionId, policy, authored) }
    }

    private fun writeEvidencePolicy(
        templateVersionId: UUID,
        bindingId: UUID,
        authored: InformationRequestTemplateEvidencePolicyRequest,
    ): InformationRequestTemplateEvidencePolicy =
        evidencePolicyRepository.save(
            InformationRequestTemplateEvidencePolicy().apply {
                templateBindingId = bindingId
                this.templateVersionId = templateVersionId
                minimumFileCount = authored.minimumFileCount
                maximumFileCount = authored.maximumFileCount
                maximumFileSizeBytes = authored.maximumFileSizeBytes
                maximumTotalSizeBytes = authored.maximumTotalSizeBytes
                minimumPageCount = authored.minimumPageCount
                maximumPageCount = authored.maximumPageCount
                issuerRequirement = authored.issuerRequirement
                jurisdictionRequirement = authored.jurisdictionRequirement
                languageRequirement = authored.languageRequirement
                issueDateRequirement = authored.issueDateRequirement
                expiryDateRequirement = authored.expiryDateRequirement
                coveragePeriodRequirement = authored.coveragePeriodRequirement
                certificationRequirement = authored.certificationRequirement
                signatureRequirement = authored.signatureRequirement
                maximumIssueAgeDays = authored.maximumIssueAgeDays
                minimumRemainingValidityDays = authored.minimumRemainingValidityDays
                minimumCoverageDays = authored.minimumCoverageDays
                coverageContinuityRequired = authored.coverageContinuityRequired
                waiverPolicy = authored.waiverPolicy
                conformancePolicy = authored.conformancePolicy
            },
        )

    private fun writeAcceptedValues(
        templateVersionId: UUID,
        policy: InformationRequestTemplateEvidencePolicy,
        authored: InformationRequestTemplateEvidencePolicyRequest,
    )
    {
        authored.acceptedValues.forEach { accepted ->
            acceptedValueRepository.save(
                InformationRequestTemplateEvidenceAcceptedValue().apply {
                    evidencePolicyId = policy.id
                    this.templateVersionId = templateVersionId
                    attribute = accepted.attribute
                    acceptedValue = accepted.acceptedValue
                },
            )
        }
    }
}
