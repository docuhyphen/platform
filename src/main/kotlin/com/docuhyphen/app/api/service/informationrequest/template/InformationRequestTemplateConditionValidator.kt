package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.service.fields.CanonicalDateTime
import com.docuhyphen.app.api.service.fields.CanonicalNumber
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.machineKeyRefusal
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.normalizeMachineKey
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.optionalContent
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuse
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuseConditionRule
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.requireDistinct
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.util.*

internal object InformationRequestTemplateConditionValidator {
    private val fieldTypeRegistry = FieldTypeRegistry()

    fun normalizeConditionRules(
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
    ): List<InformationRequestTemplateConditionRuleRequest> {
        val normalized = conditionRules.map { rule ->
            val ruleKey = normalizeMachineKey(rule.ruleKey) {
                refuseConditionRule(machineKeyRefusal("condition rule key", rule.ruleKey), rule.ruleKey)
            }
            if (rule.expressionVersion != SUPPORTED_CONDITION_EXPRESSION_VERSION) {
                refuseConditionRule(
                    "Condition rule $ruleKey uses expression version ${rule.expressionVersion}, which this runtime " +
                            "does not support",
                    ruleKey,
                )
            }
            if (rule.predicates.isEmpty()) {
                refuseConditionRule("Condition rule $ruleKey states no predicates", ruleKey)
            }
            rule.copy(
                ruleKey = ruleKey,
                predicates = rule.predicates.map { normalizeConditionPredicate(ruleKey, it) },
            )
        }
        requireDistinct(normalized.map { it.ruleKey }) { key ->
            refuseConditionRule("Two condition rules state the same rule key: $key", key)
        }
        return normalized
    }

    fun validateConditionRules(
        requirements: List<InformationRequestTemplateRequirementRequest>,
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
        groups: List<InformationRequestTemplateGroupRequest> = emptyList(),
    ) {
        val ruleByKey = conditionRules.associateBy { it.ruleKey }
        val requirementByKey = requirements.associateBy { it.requirementKey }
        val requirementKeyByField = requirements
            .mapNotNull { requirement ->
                requirement.collectedFieldDefinitionId?.let { it to requirement.requirementKey }
            }
            .toMap()

        requirements.forEach { requirement ->
            val ruleKey = requirement.conditionalRuleKey ?: return@forEach
            if (ruleKey !in ruleByKey) {
                refuse(
                    "Requirement ${requirement.requirementKey} names condition rule $ruleKey, which this template " +
                            "version does not define",
                    requirementKey = requirement.requirementKey,
                )
            }
        }

        conditionRules.forEach { rule ->
            rule.predicates.forEach { predicate ->
                predicate.sourceRequirementKey?.let { sourceKey ->
                    if (sourceKey !in requirementByKey) {
                        refuseConditionRule(
                            "Condition rule ${rule.ruleKey} reads requirement $sourceKey, which this template " +
                                    "version does not ask for",
                            rule.ruleKey,
                        )
                    }
                    if (requirementByKey[sourceKey]?.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION) {
                        refuseConditionRule(
                            "Condition rule ${rule.ruleKey} reads $sourceKey, a confirmation; a condition reads an " +
                                    "answer or a document",
                            rule.ruleKey,
                        )
                    }
                }
                predicate.fieldDefinitionId?.let { fieldId ->
                    if (fieldId !in requirementKeyByField) {
                        refuseConditionRule(
                            "Condition rule ${rule.ruleKey} reads Field $fieldId, which no requirement in this " +
                                    "template version collects",
                            rule.ruleKey,
                        )
                    }
                }
            }
        }

        validateSourceScopes(requirements, ruleByKey, requirementByKey, requirementKeyByField, groups)
        detectConditionCycles(requirements, ruleByKey, requirementKeyByField)
        validateRemovalWithinRole(requirements, conditionRules)
    }

    private fun validateSourceScopes(
        requirements: List<InformationRequestTemplateRequirementRequest>,
        ruleByKey: Map<String, InformationRequestTemplateConditionRuleRequest>,
        requirementByKey: Map<String, InformationRequestTemplateRequirementRequest>,
        requirementKeyByField: Map<UUID, String>,
        groups: List<InformationRequestTemplateGroupRequest>,
    ) {
        val parentByGroup = groups.associate { it.groupKey to it.parentGroupKey }
        requirements.forEach { requirement ->
            val rule = requirement.conditionalRuleKey?.let(ruleByKey::get) ?: return@forEach
            val readable =
                (generateSequence(requirement.occurrenceAnchorKey) { parentByGroup[it] }.toList() + listOf(null)).toSet()
            rule.predicates.forEach { predicate ->
                val sourceKey = predicate.sourceRequirementKey
                    ?: predicate.fieldDefinitionId?.let(requirementKeyByField::get)
                    ?: return@forEach
                val sourceAnchor = requirementByKey[sourceKey]?.occurrenceAnchorKey
                if (sourceAnchor !in readable) {
                    refuseConditionRule(
                        "Condition rule ${rule.ruleKey} reads $sourceKey, answered once per $sourceAnchor, but decides " +
                                "requirement ${requirement.requirementKey}, which can read only answers at the top level " +
                                "or in its own or an enclosing group",
                        rule.ruleKey,
                    )
                }
            }
        }
    }

    fun requireRemovalWithinRole(document: InformationRequestTemplateConfigurationRequest) {
        validateRemovalWithinRole(document.sections.flatMap { it.requirements }, document.conditionRules)
    }

    private fun validateRemovalWithinRole(
        requirements: List<InformationRequestTemplateRequirementRequest>,
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
    ) {
        val requirementByKey = requirements.associateBy { it.requirementKey }
        val requirementKeyByField = requirements
            .mapNotNull { requirement -> requirement.collectedFieldDefinitionId?.let { it to requirement.requirementKey } }
            .toMap()
        conditionRules
            .filter { it.hiddenDataPolicy != InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY }
            .forEach { rule ->
                val sources = rule.predicates
                    .mapNotNull { predicate ->
                        predicate.sourceRequirementKey ?: predicate.fieldDefinitionId?.let(
                            requirementKeyByField::get
                        )
                    }
                    .mapNotNull(requirementByKey::get)
                requirements
                    .filter { it.requiredness == InformationRequestRequiredness.CONDITIONAL && it.conditionalRuleKey == rule.ruleKey }
                    .forEach { governed ->
                        val source =
                            sources.firstOrNull { it.contributorRole != governed.contributorRole } ?: return@forEach
                        throw InformationRequestTemplateValidationException(
                            "Condition rule ${rule.ruleKey} would remove the hidden answer to requirement " +
                                    "${governed.requirementKey}, which the ${governed.contributorRole} role gives, while " +
                                    "its source ${source.requirementKey} is answered by the ${source.contributorRole} role, " +
                                    "so it keeps hidden answers securely instead",
                            conditionRuleKey = rule.ruleKey,
                        )
                    }
            }
    }

    private fun normalizeConditionPredicate(
        ruleKey: String,
        predicate: InformationRequestTemplateConditionPredicateRequest,
    ): InformationRequestTemplateConditionPredicateRequest {
        val sourceRequirementKey = optionalContent(predicate.sourceRequirementKey)?.let { candidate ->
            normalizeMachineKey(candidate) {
                refuseConditionRule(
                    "Condition rule $ruleKey names '$candidate' as a source requirement, which is not a usable " +
                            "requirement key",
                    ruleKey,
                )
            }
        }
        val hasRequirement = sourceRequirementKey != null
        val hasField = predicate.fieldDefinitionId != null
        if (hasRequirement == hasField) {
            refuseConditionRule(
                "Condition rule $ruleKey must name exactly one source requirement or Field",
                ruleKey,
            )
        }
        if (hasField) {
            val valueType = predicate.valueType
                ?: refuseConditionRule("Condition rule $ruleKey names a Field but no Field value type", ruleKey)
            val supported = fieldTypeRegistry.contractFor(valueType).supportedOperators
            if (predicate.operator !in supported) {
                refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator} with $valueType, which is not supported",
                    ruleKey,
                )
            }
            return predicate.copy(
                sourceRequirementKey = sourceRequirementKey,
                value = normalizedConditionValue(ruleKey, predicate, valueType),
            )
        } else {
            if (predicate.operator !in DISPOSITION_OPERATORS) {
                refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator} against a requirement disposition",
                    ruleKey,
                )
            }
            if (predicate.operator in DISPOSITION_VALUE_OPERATORS && predicate.expectedDisposition == null) {
                refuseConditionRule("Condition rule $ruleKey compares a disposition but states no disposition", ruleKey)
            }
        }
        return predicate.copy(sourceRequirementKey = sourceRequirementKey)
    }

    private fun normalizedConditionValue(
        ruleKey: String,
        predicate: InformationRequestTemplateConditionPredicateRequest,
        valueType: FieldValueType,
    ): JsonElement? {
        val stated = predicate.value?.takeUnless { it is JsonNull }
        if (predicate.operator in VALUELESS_CONDITION_OPERATORS) {
            if (stated != null) {
                refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator}, which compares no value",
                    ruleKey,
                )
            }
            return null
        }
        stated ?: refuseConditionRule("Condition rule $ruleKey compares a Field but states no value", ruleKey)
        if (predicate.operator in LIST_CONDITION_OPERATORS) {
            val entries = stated as? JsonArray
                ?: refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator}, so its value is a list of option codes",
                    ruleKey,
                )
            val codes = entries.map { optionCode(ruleKey, it) }.distinct()
            if (codes.isEmpty()) {
                refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator} but lists no option code",
                    ruleKey,
                )
            }
            return JsonArray(codes.map(::JsonPrimitive))
        }
        val literal = (stated as? JsonPrimitive)
            ?: refuseConditionRule(
                "Condition rule $ruleKey uses ${predicate.operator}, so its value is a single value",
                ruleKey,
            )
        return canonicalLiteral(ruleKey, predicate.operator, valueType, literal)
    }

    private fun canonicalLiteral(
        ruleKey: String,
        operator: FieldOperator,
        valueType: FieldValueType,
        literal: JsonPrimitive,
    ): JsonPrimitive {
        fun unreadable(expected: String): Nothing =
            refuseConditionRule(
                "Condition rule $ruleKey compares a $valueType Field with a value that is not $expected",
                ruleKey,
            )

        val content = literal.content.trim()
        return when (valueType) {
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT -> {
                if (content.isEmpty()) unreadable("a text value")
                JsonPrimitive(if (operator in EQUALITY_CONDITION_OPERATORS) content else literal.content)
            }

            FieldValueType.BOOLEAN -> JsonPrimitive(literal.booleanOrNull ?: unreadable("true or false"))

            FieldValueType.INTEGER, FieldValueType.DECIMAL -> {
                val number = content.toBigDecimalOrNull() ?: unreadable("a number")
                if (valueType == FieldValueType.INTEGER && CanonicalNumber.significantScale(number) > 0) {
                    unreadable("a whole number")
                }
                try {
                    CanonicalNumber.assertStorable(number)
                } catch (refusal: FieldValidationException) {
                    refuseConditionRule(
                        "Condition rule $ruleKey compares a number that cannot be kept: ${refusal.message}",
                        ruleKey,
                    )
                }
                JsonPrimitive(CanonicalNumber.text(valueType, number, null))
            }

            FieldValueType.DATE ->
                JsonPrimitive(
                    (runCatching { LocalDate.parse(content) }.getOrNull() ?: unreadable("an ISO date")).toString(),
                )

            FieldValueType.DATE_TIME -> {
                val reading = CanonicalDateTime.parse(content) ?: unreadable("an ISO date-time")
                JsonPrimitive(CanonicalDateTime.format(reading.instant, reading.offsetMinutes))
            }

            FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT -> JsonPrimitive(optionCode(ruleKey, literal))
        }
    }

    private fun optionCode(ruleKey: String, entry: JsonElement): String {
        val code = (entry as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()?.ifEmpty { null }
            ?: refuseConditionRule("Condition rule $ruleKey names a value that is not an option code", ruleKey)
        if (code.length > MAX_OPTION_CODE_LENGTH) {
            refuseConditionRule(
                "Condition rule $ruleKey names an option code longer than $MAX_OPTION_CODE_LENGTH characters",
                ruleKey,
            )
        }
        return code
    }

    private fun detectConditionCycles(
        requirements: List<InformationRequestTemplateRequirementRequest>,
        ruleByKey: Map<String, InformationRequestTemplateConditionRuleRequest>,
        requirementKeyByField: Map<UUID, String>,
    ) {
        val conditionalByKey = requirements
            .filter { it.requiredness == InformationRequestRequiredness.CONDITIONAL && it.conditionalRuleKey != null }
            .associateBy { it.requirementKey }
        val dependencies = conditionalByKey.mapValues { (_, requirement) ->
            ruleByKey.getValue(requirement.conditionalRuleKey!!).predicates.mapNotNull { predicate ->
                predicate.sourceRequirementKey ?: predicate.fieldDefinitionId?.let(requirementKeyByField::get)
            }.filter { it in conditionalByKey }
        }

        val checked = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val onPath = mutableSetOf<String>()

        fun visit(key: String) {
            if (key in checked) return
            if (key in onPath) {
                refuse(
                    "Conditional requirements ${path.dropWhile { it != key }.plus(key).joinToString(" then ")} " +
                            "depend on each other",
                    requirementKey = key,
                )
            }
            onPath += key
            path += key
            dependencies[key].orEmpty().forEach(::visit)
            path.removeAt(path.lastIndex)
            onPath -= key
            checked += key
        }

        dependencies.keys.forEach(::visit)
    }

    private const val SUPPORTED_CONDITION_EXPRESSION_VERSION = 1
    private const val MAX_OPTION_CODE_LENGTH = 256
    private val VALUELESS_CONDITION_OPERATORS = setOf(
        FieldOperator.IS_EMPTY,
        FieldOperator.IS_NOT_EMPTY,
    )
    private val LIST_CONDITION_OPERATORS = setOf(
        FieldOperator.IN,
        FieldOperator.NOT_IN,
    )
    private val EQUALITY_CONDITION_OPERATORS = setOf(
        FieldOperator.EQUALS,
        FieldOperator.NOT_EQUALS,
    )
    private val DISPOSITION_OPERATORS = setOf(
        FieldOperator.EQUALS,
        FieldOperator.NOT_EQUALS,
        FieldOperator.IS_EMPTY,
        FieldOperator.IS_NOT_EMPTY,
    )
    private val DISPOSITION_VALUE_OPERATORS = setOf(
        FieldOperator.EQUALS,
        FieldOperator.NOT_EQUALS,
    )
}
