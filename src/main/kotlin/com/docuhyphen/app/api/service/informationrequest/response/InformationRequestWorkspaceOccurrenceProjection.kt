package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import java.util.*

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
        val creationGroups = creationGroupKeys.mapNotNull { groupsByKey[it] }
        val creationGroupIds = creationGroups.map { it.id }.toSet()
        val enclosingGroupIds = creationGroups.flatMap { creation ->
            generateSequence(groupsByKey[creation.parentGroupKey]) { group -> groupsByKey[group.parentGroupKey] }.map { it.id }
                .toList()
        }.toSet()
        return occurrences.filter { occurrence ->
            occurrence.sourceTemplateGroupId in safeGroupIds && (
                    disclosedPaths.any { path ->
                        InformationRequestOccurrencePath.isWithin(
                            path,
                            occurrence.occurrencePath
                        )
                    } ||
                            occurrence.sourceTemplateGroupId in enclosingGroupIds ||
                            (occurrence.sourceTemplateGroupId in creationGroupIds &&
                                    occupiedPaths.none { path ->
                                        InformationRequestOccurrencePath.isWithin(
                                            path,
                                            occurrence.occurrencePath
                                        )
                                    })
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
            val ruleKey = InformationRequestGoverningCondition.ruleKeyOf(bindings[requirement.sourceTemplateBindingId])
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
            (InformationRequestOccurrencePath.isRoot(requirement.occurrencePath) || InformationRequestOccurrencePath.isWithin(
                scope,
                requirement.occurrencePath
            )) &&
                    binding != null && (
                    (predicate.sourceRequirementKey != null && predicate.sourceRequirementKey == binding.requirementKey) ||
                            (predicate.fieldDefinitionId != null && predicate.fieldDefinitionId == binding.collectedFieldDefinitionId)
                    )
        }
        sources.isNotEmpty() && sources.all { it.id in disclosedIds }
    }
}
