package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAttestationPolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import java.util.UUID

internal object InformationRequestTemplateSubmissionPolicyValidator
{
    fun normalize(document: InformationRequestTemplateConfigurationRequest): InformationRequestTemplateConfigurationRequest
    {
        val sections = normalizeStages(document)
        if (document.submissionMode == InformationRequestSubmissionMode.STAGED)
        {
            validateStageContainment(document.copy(sections = sections))
        }
        return document.copy(
            sections = sections.map { section ->
                section.copy(requirements = section.requirements.map(::normalizeAttestationPolicy))
            },
        )
    }

    private fun normalizeStages(document: InformationRequestTemplateConfigurationRequest): List<InformationRequestTemplateSectionRequest>
    {
        val staged = document.submissionMode == InformationRequestSubmissionMode.STAGED
        if (!staged && document.submissionStageOrdering != InformationRequestSubmissionStageOrdering.ANY_ORDER)
        {
            throw InformationRequestTemplateValidationException(
                "A whole-package template version submits everything at once, so it has no stage order",
            )
        }
        return document.sections.map { section ->
            val stated = section.submissionStageKey?.trim()?.ifBlank { null }
            when
            {
                staged && stated == null -> throw InformationRequestTemplateValidationException(
                    "Section ${section.sectionKey} names no submission stage, and every section of a staged " +
                        "template version is submitted in one",
                    sectionKey = section.sectionKey,
                )
                !staged && stated != null -> throw InformationRequestTemplateValidationException(
                    "Section ${section.sectionKey} names submission stage $stated, but this template version " +
                        "submits everything at once",
                    sectionKey = section.sectionKey,
                )
                stated == null -> section.copy(submissionStageKey = null)
                else -> section.copy(
                    submissionStageKey = InformationRequestTemplateKey.normalizeOrNull(stated)
                        ?: throw InformationRequestTemplateValidationException(
                            InformationRequestTemplateKey.refusalFor("submission stage key", stated),
                            sectionKey = section.sectionKey,
                        ),
                )
            }
        }
    }

    private fun validateStageContainment(document: InformationRequestTemplateConfigurationRequest)
    {
        val stageOrder = document.sections.mapNotNull { it.submissionStageKey }.distinct()
        val stageByRequirement = document.sections.flatMap { section ->
            section.requirements.map { it.requirementKey to section.submissionStageKey!! }
        }.toMap()
        val requirements = document.sections.flatMap { it.requirements }
        validateConditionStages(document, requirements, stageByRequirement, stageOrder)
        validateGroupStages(document.groups, requirements, stageByRequirement)
    }

    private fun validateConditionStages(
        document: InformationRequestTemplateConfigurationRequest,
        requirements: List<InformationRequestTemplateRequirementRequest>,
        stageByRequirement: Map<String, String>,
        stageOrder: List<String>,
    )
    {
        val sequential = document.submissionStageOrdering == InformationRequestSubmissionStageOrdering.SEQUENTIAL
        val ruleByKey = document.conditionRules.associateBy { it.ruleKey }
        val requirementKeyByField: Map<UUID, String> = requirements
            .mapNotNull { requirement -> requirement.collectedFieldDefinitionId?.let { it to requirement.requirementKey } }
            .toMap()

        requirements.forEach { conditional ->
            val rule = conditional.conditionalRuleKey?.let(ruleByKey::get) ?: return@forEach
            val conditionalStage = stageByRequirement.getValue(conditional.requirementKey)
            rule.predicates.forEach { predicate ->
                val sourceKey = predicate.sourceRequirementKey
                    ?: predicate.fieldDefinitionId?.let(requirementKeyByField::get)
                    ?: return@forEach
                val sourceStage = stageByRequirement[sourceKey] ?: return@forEach
                val earlier = stageOrder.indexOf(sourceStage) < stageOrder.indexOf(conditionalStage)
                if (sourceStage != conditionalStage && !(sequential && earlier))
                {
                    throw InformationRequestTemplateValidationException(
                        "Requirement ${conditional.requirementKey} is submitted in stage $conditionalStage but its " +
                            "condition ${rule.ruleKey} reads $sourceKey from stage $sourceStage, which could change " +
                            "after this stage is submitted",
                        requirementKey = conditional.requirementKey,
                    )
                }
            }
        }
    }

    private fun validateGroupStages(
        groups: List<InformationRequestTemplateGroupRequest>,
        requirements: List<InformationRequestTemplateRequirementRequest>,
        stageByRequirement: Map<String, String>,
    )
    {
        val childrenByParent = groups.groupBy { it.parentGroupKey }
        fun family(groupKey: String): Set<String> =
            setOf(groupKey) + childrenByParent[groupKey].orEmpty().flatMap { family(it.groupKey) }

        groups.forEach { group ->
            val members = family(group.groupKey)
            val stages = requirements
                .filter { it.occurrenceAnchorKey in members }
                .map { stageByRequirement.getValue(it.requirementKey) }
                .toSet()
            if (stages.size > 1)
            {
                throw InformationRequestTemplateValidationException(
                    "Repeatable group ${group.groupKey} and its nested groups are answered in stages " +
                        "${stages.sorted().joinToString()}, and adding or removing one of its occurrences " +
                        "would change more than one stage",
                    groupKey = group.groupKey,
                )
            }
        }
    }

    private fun normalizeAttestationPolicy(
        requirement: InformationRequestTemplateRequirementRequest,
    ): InformationRequestTemplateRequirementRequest
    {
        val key = requirement.requirementKey
        val stated = requirement.attestationPolicy
        if (requirement.requirementType != InformationRequestRequirementType.RESPONSE_ATTESTATION)
        {
            if (stated != null)
            {
                throw InformationRequestTemplateValidationException(
                    "An attestation policy belongs to a response attestation, and requirement $key asks for " +
                        "${requirement.requirementType}",
                    requirementKey = key,
                )
            }
            return requirement
        }
        if (requirement.responseMode !in ANSWERABLE_RESPONSE_MODES)
        {
            if (stated != null)
            {
                throw InformationRequestTemplateValidationException(
                    "Requirement $key is ${requirement.responseMode} to the party it nominates, so nobody can " +
                        "make its assertion under a policy",
                    requirementKey = key,
                )
            }
            return requirement
        }

        val policy = stated ?: InformationRequestTemplateAttestationPolicyRequest()
        val roles = policy.requiredRoles.ifEmpty { listOf(requirement.contributorRole) }
        if (roles.distinct().size != roles.size)
        {
            throw InformationRequestTemplateValidationException(
                "Requirement $key names the same party role twice in its attestation policy",
                requirementKey = key,
            )
        }
        val minimumAssents = policy.minimumAssentCount ?: roles.size
        if (minimumAssents < roles.size)
        {
            throw InformationRequestTemplateValidationException(
                "Requirement $key needs an assent from each of ${roles.size} roles, so it cannot be satisfied " +
                    "by $minimumAssents",
                requirementKey = key,
            )
        }
        val validity = policy.validityHours
        if (validity != null && validity < 1)
        {
            throw InformationRequestTemplateValidationException(
                "Requirement $key keeps an assent valid for $validity hours, and an assent has to stay valid " +
                    "for at least one",
                requirementKey = key,
            )
        }
        return requirement.copy(
            attestationPolicy = policy.copy(requiredRoles = roles, minimumAssentCount = minimumAssents),
        )
    }

    private val ANSWERABLE_RESPONSE_MODES = setOf(
        InformationRequestResponseMode.PROVIDE,
        InformationRequestResponseMode.PROVIDE_ONCE,
    )
}
