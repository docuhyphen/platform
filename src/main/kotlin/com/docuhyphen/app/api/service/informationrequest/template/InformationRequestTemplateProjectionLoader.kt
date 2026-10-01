package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateProjectionBindingConfiguration
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateAttestationPolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateAttestationRoleRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateConditionPredicateLiteralRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateConditionPredicateRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateConditionRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateReviewStageRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateReviewStageSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Reads one Template Version's whole configuration back as the document it was authored as.
 *
 * Everything a version states is read in one pass per table and then assembled in memory, so
 * projecting a version costs a fixed number of queries rather than one per requirement. That matters
 * because this is the read every author screen, every request creation, and every reconstruction of
 * what was asked years ago goes through.
 *
 * Relations between requirements are returned as stable requirement keys rather than binding
 * identifiers. A binding identifies one version's statement and means nothing to a caller comparing
 * versions or reading a recorded response, whereas the key is the thing that stays the same.
 */
@ApplicationScoped
class InformationRequestTemplateProjectionLoader @Inject constructor(
    private val sectionRepository: InformationRequestTemplateSectionRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val requirementRepository: InformationRequestTemplateRequirementRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val evidencePolicyRepository: InformationRequestTemplateEvidencePolicyRepository,
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository,
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository,
    private val evidenceLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val conditionRuleRepository: InformationRequestTemplateConditionRuleRepository,
    private val conditionPredicateRepository: InformationRequestTemplateConditionPredicateRepository,
    private val conditionPredicateLiteralRepository: InformationRequestTemplateConditionPredicateLiteralRepository,
    private val attestationPolicyRepository: InformationRequestTemplateAttestationPolicyRepository,
    private val attestationRoleRepository: InformationRequestTemplateAttestationRoleRepository,
    private val reviewStageRepository: InformationRequestTemplateReviewStageRepository,
    private val reviewStageSectionRepository: InformationRequestTemplateReviewStageSectionRepository,
)
{
    fun loadVersion(version: InformationRequestTemplateVersion): InformationRequestTemplateVersionDto
    {
        val bindings = bindingRepository.findOrdered(version.id)
        val requirementsById = requirementRepository
            .findAllByDefinition(version.templateDefinitionId)
            .associateBy { it.id }
        val keyByBindingId = bindings.associate { binding ->
            binding.id to requirementsById.getValue(binding.templateRequirementId).requirementKey
        }
        val bindingsBySection = bindings.groupBy { it.templateSectionId }
        val configuration = loadBindingConfiguration(version.id)

        val storedSections = sectionRepository.findOrdered(version.id)
        val sections = storedSections.map { section ->
            InformationRequestTemplateDtoMapper.toDto(
                section = section,
                requirements = bindingsBySection[section.id]
                    .orEmpty()
                    .sortedBy { it.displayOrder }
                    .map { binding ->
                        requirementDto(binding, requirementsById, keyByBindingId, configuration)
                    },
            )
        }

        return InformationRequestTemplateDtoMapper.toDto(
            version = version,
            sections = sections,
            groups = loadGroups(version.id),
            conditionRules = loadConditionRules(version.id),
            requiredCapabilities = capabilityRepository.findForVersion(version.id)
                .map(InformationRequestTemplateDtoMapper::toDto),
            reviewStages = loadReviewStages(version.id, storedSections.associate { it.id to it.sectionKey }),
        )
    }

    private fun loadReviewStages(
        templateVersionId: UUID,
        sectionKeyById: Map<UUID, String>,
    ): List<InformationRequestTemplateReviewStageDto>
    {
        val coveredByStage = reviewStageSectionRepository.findForVersion(templateVersionId).groupBy { it.reviewStageId }
        val orderedKeys = sectionKeyById.values.toList()
        return reviewStageRepository.findForVersion(templateVersionId).map { stage ->
            val covered = coveredByStage[stage.id].orEmpty().mapNotNull { sectionKeyById[it.templateSectionId] }.toSet()
            InformationRequestTemplateDtoMapper.toDto(stage, orderedKeys.filter { it in covered })
        }
    }

    private fun loadGroups(templateVersionId: UUID): List<InformationRequestTemplateGroupDto>
    {
        val groups = groupRepository.findForVersion(templateVersionId)
        val keyById = groups.associate { it.id to it.groupKey }
        return groups.map { group ->
            InformationRequestTemplateDtoMapper.toDto(group, group.parentGroupId?.let(keyById::get))
        }
    }

    fun loadConditionRules(templateVersionId: UUID): List<InformationRequestTemplateConditionRuleDto>
    {
        val predicates = conditionPredicateRepository.findForVersion(templateVersionId)
        val literalsByPredicate = conditionPredicateLiteralRepository.findForVersion(templateVersionId)
            .groupBy { it.conditionPredicateId }
        val predicatesByRule = predicates
            .sortedBy { it.displayOrder }
            .groupBy { it.conditionRuleId }

        return conditionRuleRepository.findForVersion(templateVersionId).map { rule ->
            InformationRequestTemplateDtoMapper.toDto(
                rule = rule,
                predicates = predicatesByRule[rule.id].orEmpty().map { predicate ->
                    InformationRequestTemplateDtoMapper.toDto(
                        predicate = predicate,
                        literalValues = literalsByPredicate[predicate.id]
                            .orEmpty()
                            .sortedBy { it.displayOrder }
                            .map { it.literalValue },
                    )
                },
            )
        }
    }

    private fun requirementDto(
        binding: InformationRequestTemplateRequirementBinding,
        requirementsById: Map<UUID, InformationRequestTemplateRequirement>,
        keyByBindingId: Map<UUID, String>,
        configuration: InformationRequestTemplateProjectionBindingConfiguration,
    ): InformationRequestTemplateRequirementDto
    {
        val policy = configuration.policiesByBinding[binding.id]
        return InformationRequestTemplateDtoMapper.toDto(
            binding = binding,
            requirement = requirementsById.getValue(binding.templateRequirementId),
            // Sorted the way an authored set is normalized, so what was stated reads back unchanged
            // whatever order the rows happen to come out of storage in.
            permittedDispositions = configuration.dispositionsByBinding[binding.id]
                .orEmpty()
                .map { it.disposition }
                .sortedBy { it.ordinal },
            evidencePolicy = policy?.let {
                InformationRequestTemplateDtoMapper.toDto(
                    it,
                    configuration.acceptedValuesByPolicy[it.id].orEmpty(),
                )
            },
            substituteRequirementKeys = configuration.substitutesByBinding[binding.id]
                .orEmpty()
                .mapNotNull { keyByBindingId[it.substituteTemplateBindingId] },
            supportingEvidenceRequirementKeys = configuration.evidenceLinksByBinding[binding.id]
                .orEmpty()
                .mapNotNull { keyByBindingId[it.supportingTemplateBindingId] },
            attestationPolicy = configuration.attestationPoliciesByBinding[binding.id]?.let { attestation ->
                InformationRequestTemplateDtoMapper.toDto(
                    attestation,
                    configuration.attestationRolesByPolicy[attestation.id].orEmpty(),
                )
            },
        )
    }

    private fun loadBindingConfiguration(templateVersionId: UUID) = InformationRequestTemplateProjectionBindingConfiguration(
        dispositionsByBinding = dispositionRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        policiesByBinding = evidencePolicyRepository.findForVersion(templateVersionId)
            .associateBy { it.templateBindingId },
        acceptedValuesByPolicy = acceptedValueRepository.findForVersion(templateVersionId)
            .groupBy { it.evidencePolicyId },
        substitutesByBinding = substituteRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        evidenceLinksByBinding = evidenceLinkRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        attestationPoliciesByBinding = attestationPolicyRepository.findForVersion(templateVersionId)
            .associateBy { it.templateBindingId },
        attestationRolesByPolicy = attestationRoleRepository.findForVersion(templateVersionId)
            .groupBy { it.attestationPolicyId },
    )
}
