package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.InformationRequestTemplateProjectionDtoMapper
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateProjectionBindingConfiguration
import com.docuhyphen.app.api.repository.informationrequest.template.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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
        val configuration = loadBindingConfiguration(version.id)

        val storedSections = sectionRepository.findOrdered(version.id)
        val sections = InformationRequestTemplateProjectionDtoMapper.toSectionDtos(
            storedSections,
            bindings,
            requirementsById,
            configuration,
        )

        val projected = InformationRequestTemplateDtoMapper.toDto(
            version = version,
            sections = sections,
            groups = loadGroups(version.id),
            conditionRules = loadConditionRules(version.id),
            requiredCapabilities = capabilityRepository.findForVersion(version.id)
                .map(InformationRequestTemplateDtoMapper::toDto),
            reviewStages = loadReviewStages(version.id, storedSections.associate { it.id to it.sectionKey }),
        )
        return projected.copy(contentHashSha256 = InformationRequestTemplateVersionContentHash.of(projected))
    }

    private fun loadReviewStages(
        templateVersionId: UUID,
        sectionKeyById: Map<UUID, String>,
    ): List<InformationRequestTemplateReviewStageDto>
    {
        val stageSections = reviewStageSectionRepository.findForVersion(templateVersionId)
        val stages = reviewStageRepository.findForVersion(templateVersionId)
        return InformationRequestTemplateProjectionDtoMapper.toReviewStageDtos(stages, stageSections, sectionKeyById)
    }

    private fun loadGroups(templateVersionId: UUID): List<InformationRequestTemplateGroupDto> =
        InformationRequestTemplateProjectionDtoMapper.toGroupDtos(groupRepository.findForVersion(templateVersionId))

    fun loadConditionRules(templateVersionId: UUID): List<InformationRequestTemplateConditionRuleDto>
    {
        val predicates = conditionPredicateRepository.findForVersion(templateVersionId)
        val literals = conditionPredicateLiteralRepository.findForVersion(templateVersionId)
        val rules = conditionRuleRepository.findForVersion(templateVersionId)
        return InformationRequestTemplateProjectionDtoMapper.toConditionRuleDtos(rules, predicates, literals)
    }

    private fun loadBindingConfiguration(templateVersionId: UUID) =
        InformationRequestTemplateProjectionBindingConfiguration(
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
