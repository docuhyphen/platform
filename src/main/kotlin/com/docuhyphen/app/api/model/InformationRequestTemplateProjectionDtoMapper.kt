package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateProjectionBindingConfiguration
import java.util.*

object InformationRequestTemplateProjectionDtoMapper
{
    internal fun toSectionDtos(
        sections: List<InformationRequestTemplateSection>,
        bindings: List<InformationRequestTemplateRequirementBinding>,
        requirementsById: Map<UUID, InformationRequestTemplateRequirement>,
        configuration: InformationRequestTemplateProjectionBindingConfiguration,
    ): List<InformationRequestTemplateSectionDto>
    {
        val keyByBindingId = bindings.associate { binding ->
            binding.id to requirementsById.getValue(binding.templateRequirementId).requirementKey
        }
        val bindingsBySection = bindings.groupBy { it.templateSectionId }
        return sections.map { section ->
            InformationRequestTemplateDtoMapper.toDto(
                section = section,
                requirements = bindingsBySection[section.id]
                    .orEmpty()
                    .sortedBy { it.displayOrder }
                    .map { binding ->
                        toRequirementDto(binding, requirementsById, keyByBindingId, configuration)
                    },
            )
        }
    }

    internal fun toRequirementDto(
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
                .mapNotNull { keyByBindingId[it.substituteTemplateBindingId] }
                .sorted(),
            supportingEvidenceRequirementKeys = configuration.evidenceLinksByBinding[binding.id]
                .orEmpty()
                .mapNotNull { keyByBindingId[it.supportingTemplateBindingId] }
                .sorted(),
            attestationPolicy = configuration.attestationPoliciesByBinding[binding.id]?.let { attestation ->
                InformationRequestTemplateDtoMapper.toDto(
                    attestation,
                    configuration.attestationRolesByPolicy[attestation.id].orEmpty(),
                )
            },
        )
    }

    fun toGroupDtos(groups: List<InformationRequestTemplateRequirementGroup>): List<InformationRequestTemplateGroupDto>
    {
        val ordered = groups.sortedBy { it.groupKey }
        val keyById = ordered.associate { it.id to it.groupKey }
        return ordered.map { group ->
            InformationRequestTemplateDtoMapper.toDto(group, group.parentGroupId?.let(keyById::get))
        }
    }

    fun toReviewStageDtos(
        stages: List<InformationRequestTemplateReviewStage>,
        stageSections: List<InformationRequestTemplateReviewStageSection>,
        sectionKeyById: Map<UUID, String>,
    ): List<InformationRequestTemplateReviewStageDto>
    {
        val coveredByStage = stageSections.groupBy { it.reviewStageId }
        val orderedKeys = sectionKeyById.values.toList()
        return stages.map { stage ->
            val covered = coveredByStage[stage.id].orEmpty().mapNotNull { sectionKeyById[it.templateSectionId] }.toSet()
            InformationRequestTemplateDtoMapper.toDto(stage, orderedKeys.filter { it in covered })
        }
    }

    fun toConditionRuleDtos(
        rules: List<InformationRequestTemplateConditionRule>,
        predicates: List<InformationRequestTemplateConditionPredicate>,
        literals: List<InformationRequestTemplateConditionPredicateLiteral>,
    ): List<InformationRequestTemplateConditionRuleDto>
    {
        val literalsByPredicate = literals.groupBy { it.conditionPredicateId }
        val predicatesByRule = predicates
            .sortedBy { it.displayOrder }
            .groupBy { it.conditionRuleId }

        return rules.sortedBy { it.ruleKey }.map { rule ->
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
}
