package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import java.util.*

internal object InformationRequestRecipientTemplateProjection {
    fun recipientSafe(
        templateVersion: InformationRequestTemplateVersionDto,
        disclosedBindingIds: Set<UUID>,
        safeExtraGroupKeys: Set<String>,
    ): InformationRequestTemplateVersionDto {
        val fullRequirements = templateVersion.sections.flatMap { it.requirements }
        val disclosedRequirementKeys = fullRequirements
            .filter { it.id in disclosedBindingIds }
            .map { it.requirementKey }
            .toSet()
        val disclosedFieldIds = fullRequirements
            .filter { it.id in disclosedBindingIds }
            .mapNotNull { it.collectedFieldDefinitionId }
            .toSet()
        val safeRules = templateVersion.conditionRules.filter { rule ->
            fullRequirements.any {
                it.id in disclosedBindingIds && InformationRequestGoverningCondition.ruleKeyOf(it) == rule.ruleKey
            } &&
                    rule.predicates.all { predicate ->
                        (predicate.sourceRequirementKey == null || predicate.sourceRequirementKey in disclosedRequirementKeys) &&
                                (predicate.fieldDefinitionId == null || predicate.fieldDefinitionId in disclosedFieldIds)
                    }
        }
        val safeRuleKeys = safeRules.map { it.ruleKey }.toSet()
        val safeSections = templateVersion.sections.mapNotNull { section ->
            val requirements = section.requirements
                .filter { it.id in disclosedBindingIds }
                .map { requirement ->
                    requirement.copy(
                        conditionalRuleKey = InformationRequestGoverningCondition.ruleKeyOf(requirement)
                            ?.takeIf { it in safeRuleKeys },
                        substituteRequirementKeys = requirement.substituteRequirementKeys.filter { it in disclosedRequirementKeys },
                        supportingEvidenceRequirementKeys = requirement.supportingEvidenceRequirementKeys.filter { it in disclosedRequirementKeys },
                    )
                }
            if (requirements.isEmpty()) null else section.copy(requirements = requirements)
        }
        val safeGroupKeys = recipientSafeGroupKeys(
            templateVersion.groups,
            safeSections.flatMap { it.requirements },
            safeExtraGroupKeys,
        )
        return templateVersion.copy(
            sections = safeSections,
            groups = templateVersion.groups.filter { it.groupKey in safeGroupKeys },
            conditionRules = safeRules,
        )
    }

    fun forParticipant(templateVersion: InformationRequestTemplateVersionDto): InformationRequestTemplateVersionDto =
        templateVersion.copy(
            reviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL,
            reviewStages = emptyList(),
            schemaVersionId = null,
            contentHashSha256 = null,
            factReusePurposeKey = null,
        )

    private fun recipientSafeGroupKeys(
        groups: List<InformationRequestTemplateGroupDto>,
        requirements: List<InformationRequestTemplateRequirementDto>,
        extraGroupKeys: Set<String>,
    ): Set<String> {
        val byKey = groups.associateBy { it.groupKey }
        val safe = mutableSetOf<String>()
        fun addWithParents(groupKey: String?) {
            val group = groupKey?.let(byKey::get) ?: return
            if (!safe.add(group.groupKey)) return
            addWithParents(group.parentGroupKey)
        }
        requirements.forEach { addWithParents(it.occurrenceAnchorKey) }
        extraGroupKeys.forEach { addWithParents(it) }
        return safe
    }
}
