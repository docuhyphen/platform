package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import java.util.UUID

object InformationRequestWorkspaceOccurrenceProjection
{
    fun occurrences(
        occurrences: List<InformationRequestGroupOccurrence>,
        templateVersion: InformationRequestTemplateVersionDto,
        disclosedRequirements: List<InformationRequestRequirement>,
        activeRequirements: List<InformationRequestRequirement>,
        creationGroupKeys: Set<String>,
    ): List<InformationRequestGroupOccurrence>
    {
        val safeGroupIds = templateVersion.groups.map { it.id }.toSet()
        val disclosedPaths = disclosedRequirements.map { it.occurrencePath }.toSet()
        val occupiedPaths = activeRequirements.map { it.occurrencePath }.toSet()
        val groupsByKey = templateVersion.groups.associateBy { it.groupKey }
        val creationGroupIds = creationGroupKeys.flatMap { key ->
            generateSequence(groupsByKey[key]) { group -> groupsByKey[group.parentGroupKey] }.map { it.id }.toList()
        }.toSet()
        return occurrences.filter { occurrence ->
            occurrence.sourceTemplateGroupId in safeGroupIds && (
                disclosedPaths.any { path -> isWithin(path, occurrence.occurrencePath) } ||
                    (occurrence.sourceTemplateGroupId in creationGroupIds &&
                        occupiedPaths.none { path -> isWithin(path, occurrence.occurrencePath) })
                )
        }
    }

    fun conditions(
        evaluations: List<InformationRequestConditionEvaluationProjection>,
        templateVersion: InformationRequestTemplateVersionDto,
        disclosedRequirements: List<InformationRequestRequirement>,
        activeRequirements: List<InformationRequestRequirement>,
    ): List<InformationRequestConditionEvaluationProjection>
    {
        val safeRules = templateVersion.conditionRules.associateBy { it.ruleKey }
        val bindings = templateVersion.sections.flatMap { it.requirements }.associateBy { it.id }
        val disclosedIds = disclosedRequirements.map { it.id }.toSet()
        val disclosedEvaluations = disclosedRequirements.mapNotNull { requirement ->
            val ruleKey = bindings[requirement.sourceTemplateBindingId]?.conditionalRuleKey
                ?.takeIf { it in safeRules } ?: return@mapNotNull null
            evaluations.firstOrNull { it.ruleKey == ruleKey && it.occurrencePath == requirement.occurrencePath }
                ?: evaluations.firstOrNull {
                    it.ruleKey == ruleKey && InformationRequestOccurrencePath.isRoot(it.occurrencePath)
                }
        }.toSet()
        return evaluations.filter {
            it in disclosedEvaluations && sourcesDisclosed(
                it.occurrencePath, safeRules.getValue(it.ruleKey), bindings, activeRequirements, disclosedIds,
            )
        }
    }

    private fun sourcesDisclosed(
        scope: String,
        rule: InformationRequestTemplateConditionRuleDto,
        bindings: Map<UUID, InformationRequestTemplateRequirementDto>,
        requirements: List<InformationRequestRequirement>,
        disclosedIds: Set<UUID>,
    ): Boolean = rule.predicates.all { predicate ->
        val sources = requirements.filter { requirement ->
            val binding = bindings[requirement.sourceTemplateBindingId]
            (InformationRequestOccurrencePath.isRoot(requirement.occurrencePath) || isWithin(scope, requirement.occurrencePath)) &&
                binding != null && (
                (predicate.sourceRequirementKey != null && predicate.sourceRequirementKey == binding.requirementKey) ||
                    (predicate.fieldDefinitionId != null && predicate.fieldDefinitionId == binding.collectedFieldDefinitionId)
                )
        }
        sources.isNotEmpty() && sources.all { it.id in disclosedIds }
    }

    private fun isWithin(path: String, ancestor: String): Boolean =
        path == ancestor || path.startsWith("$ancestor/")
}
