package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuse
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuseConditionRule
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuseGroup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal object InformationRequestTemplateTextLimits {
    const val KEY = 128
    const val COMPARTMENT_KEY = 64
    const val TITLE = 255
    const val DISPLAY_NAME = 255
    const val DESCRIPTION = 1024
    const val PROMPT = 1024
    const val HELP_TEXT = 2048
    const val ACCEPTED_VALUE = 255
    const val CONDITION_VALUE = 256

    fun requireDefinitionWithinLimits(
        namespace: String,
        templateKey: String,
        displayName: String,
        description: String?
    ) {
        val refusal: (String) -> Nothing = { refuse(it) }
        requireWithin(namespace, KEY, "The namespace", refusal)
        requireWithin(templateKey, KEY, "The Template key", refusal)
        requireWithin(displayName, DISPLAY_NAME, "The name", refusal)
        requireWithin(description, DESCRIPTION, "The description", refusal)
    }

    fun requireWithinLimits(request: InformationRequestTemplateConfigurationRequest) {
        requireWithin(request.factReusePurposeKey, KEY, "The reuse purpose key") { refuse(it) }
        request.groups.forEach { group ->
            val inGroup: (String) -> Nothing = { refuseGroup(it, group.groupKey) }
            requireWithin(group.groupKey, KEY, "A repeating group key", inGroup)
            requireWithin(group.parentGroupKey, KEY, "Group ${group.groupKey}: its parent group key", inGroup)
        }
        request.conditionRules.forEach { rule ->
            val inRule: (String) -> Nothing = { refuseConditionRule(it, rule.ruleKey) }
            requireWithin(rule.ruleKey, KEY, "A condition rule key", inRule)
            rule.predicates.forEach { predicate ->
                requireWithin(
                    predicate.sourceRequirementKey,
                    KEY,
                    "Condition rule ${rule.ruleKey}: a Requirement key it reads",
                    inRule,
                )
                literalsOf(predicate.value).forEach { literal ->
                    requireWithin(
                        literal,
                        CONDITION_VALUE,
                        "Condition rule ${rule.ruleKey}: a value it compares",
                        inRule
                    )
                }
            }
        }
        request.reviewStages.forEach { stage ->
            val inStage: (String) -> Nothing = {
                throw InformationRequestTemplateValidationException(it, reviewStageKey = stage.stageKey)
            }
            requireWithin(stage.stageKey, KEY, "A review stage key", inStage)
            requireWithin(stage.title, TITLE, "Review stage ${stage.stageKey}: its title", inStage)
        }
        request.sections.forEach(::requireSectionWithinLimits)
    }

    private fun requireSectionWithinLimits(section: InformationRequestTemplateSectionRequest) {
        val key = section.sectionKey
        val inSection: (String) -> Nothing = { refuse(it, sectionKey = key) }
        requireWithin(key, KEY, "A section key", inSection)
        requireWithin(section.title, TITLE, "Section $key: its title", inSection)
        requireWithin(section.helpText, HELP_TEXT, "Section $key: its help text", inSection)
        requireWithin(section.submissionStageKey, KEY, "Section $key: its submission stage key", inSection)
        section.requirements.forEach { requireRequirementWithinLimits(key, it) }
    }

    private fun requireRequirementWithinLimits(
        sectionKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ) {
        val key = requirement.requirementKey
        val inRequirement: (String) -> Nothing = { refuse(it, sectionKey = sectionKey, requirementKey = key) }
        requireWithin(key, KEY, "A Requirement key", inRequirement)
        requireWithin(requirement.prompt, PROMPT, "Requirement $key: its prompt", inRequirement)
        requireWithin(requirement.helpText, HELP_TEXT, "Requirement $key: its help text", inRequirement)
        requireWithin(
            requirement.confidentialityCompartmentKey,
            COMPARTMENT_KEY,
            "Requirement $key: its confidentiality compartment key",
            inRequirement,
        )
        requireWithin(requirement.conditionalRuleKey, KEY, "Requirement $key: its condition rule key", inRequirement)
        requireWithin(requirement.occurrenceAnchorKey, KEY, "Requirement $key: its repeating group key", inRequirement)
        requirement.evidencePolicy?.acceptedValues.orEmpty().forEach { accepted ->
            requireWithin(accepted.acceptedValue, ACCEPTED_VALUE, "Requirement $key: an accepted value", inRequirement)
        }
    }

    private fun literalsOf(value: JsonElement?): List<String> = when (value) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOf(value.content)
        is JsonArray -> value.flatMap(::literalsOf)
        else -> emptyList()
    }

    private fun requireWithin(value: String?, limit: Int, what: String, refusal: (String) -> Nothing) {
        if (value != null && value.length > limit) refusal("$what is longer than $limit characters")
    }
}
