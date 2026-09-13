package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
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
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
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

        val sections = sectionRepository.findOrdered(version.id).map { section ->
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
        )
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
        configuration: BindingConfiguration,
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
        )
    }

    private fun loadBindingConfiguration(templateVersionId: UUID) = BindingConfiguration(
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
    )

    /** Everything one version states about its requirements, indexed by the row it belongs to. */
    private data class BindingConfiguration(
        val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
        val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
        val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
        val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
        val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
    )
}
