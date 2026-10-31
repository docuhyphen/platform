package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuse
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuseConditionRule
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.requireDistinct
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestTemplateConfigurationValidator
{
    fun normalize(
        request: InformationRequestTemplateConfigurationRequest,
    ): InformationRequestTemplateConfigurationRequest
    {
        requireWithinSize(request)
        val conditionRules =
            InformationRequestTemplateConditionValidator.normalizeConditionRules(request.conditionRules)
        val groups = InformationRequestTemplateGroupValidator.normalizeGroups(request.groups)
        val groupKeys = groups.map { it.groupKey }.toSet()

        val sections = request.sections.map(InformationRequestTemplateSectionValidator::normalizeSection)
        requireDistinct(sections.map { it.sectionKey }) { key ->
            refuse("Two sections state the same section key: $key", sectionKey = key)
        }

        val requirements = sections.flatMap { it.requirements }
        requireDistinct(requirements.map { it.requirementKey }) { key ->
            refuse(
                "One template version states one thing about one requirement, and $key is stated twice",
                requirementKey = key,
            )
        }

        val typeByKey = requirements.associate { it.requirementKey to it.requirementType }
        requirements.forEach { InformationRequestTemplateRelationValidator.validateRelations(it, typeByKey) }
        InformationRequestTemplateRelationValidator.validateSubstitutionIsFlat(requirements)
        InformationRequestTemplateRelationValidator.validateSubstituteScopes(requirements)
        InformationRequestTemplateSectionValidator.validateEachFieldIsCollectedOnce(requirements)
        requirements.forEach { InformationRequestTemplateGroupValidator.validateOccurrenceAnchor(it, groupKeys) }
        InformationRequestTemplateGroupValidator.validateEveryGroupAnchorsARequirement(groups, requirements)
        InformationRequestTemplateConditionValidator.validateConditionRules(requirements, conditionRules, groups)

        return InformationRequestTemplateReviewPlanValidator.normalize(
            InformationRequestTemplateSubmissionPolicyValidator.normalize(
                request.copy(sections = sections, groups = groups, conditionRules = conditionRules),
            ),
        ).also(InformationRequestTemplateTextLimits::requireWithinLimits)
    }

    private fun requireWithinSize(request: InformationRequestTemplateConfigurationRequest) {
        if (request.sections.sumOf { it.requirements.size } > MAXIMUM_REQUIREMENTS) {
            refuse("A template version states at most $MAXIMUM_REQUIREMENTS requirements")
        }
        if (request.groups.size > MAXIMUM_GROUPS) {
            refuse("A template version states at most $MAXIMUM_GROUPS repeatable groups")
        }
        if (request.conditionRules.size > MAXIMUM_CONDITION_RULES) {
            refuse("A template version states at most $MAXIMUM_CONDITION_RULES condition rules")
        }
        request.conditionRules.firstOrNull { it.predicates.size > MAXIMUM_RULE_PREDICATES }?.let { rule ->
            refuseConditionRule(
                "Condition rule ${rule.ruleKey} states at most $MAXIMUM_RULE_PREDICATES predicates",
                rule.ruleKey,
            )
        }
    }

    private companion object
    {
        const val MAXIMUM_REQUIREMENTS = 500
        const val MAXIMUM_GROUPS = 100
        const val MAXIMUM_CONDITION_RULES = 500
        const val MAXIMUM_RULE_PREDICATES = 50
    }
}
