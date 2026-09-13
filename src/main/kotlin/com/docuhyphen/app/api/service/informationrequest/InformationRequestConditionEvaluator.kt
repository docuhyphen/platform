package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.service.fields.CanonicalDateTime
import com.docuhyphen.app.api.service.fields.CanonicalFieldValue
import com.docuhyphen.app.api.service.fields.FieldOperator
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class InformationRequestConditionEvaluationState
{
    TRUE,
    FALSE,
    UNKNOWN,
}

data class InformationRequestConditionEvaluationProjection(
    val ruleKey: String,
    val expressionVersion: Int,
    val state: InformationRequestConditionEvaluationState,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
    val sourceRequirementKeys: Set<String>,
    val fieldDefinitionIds: Set<UUID>,
    val occurrencePath: String = InformationRequestOccurrencePath.ROOT,
)

@ApplicationScoped
class InformationRequestConditionEvaluator
{
    fun evaluate(
        rules: List<InformationRequestTemplateConditionRuleRequest>,
        fieldValuesByDefinitionId: Map<UUID, CanonicalFieldValue?> = emptyMap(),
        dispositionsByRequirementKey: Map<String, InformationRequestResponseDisposition?> = emptyMap(),
        occurrencePath: String = InformationRequestOccurrencePath.ROOT,
    ): List<InformationRequestConditionEvaluationProjection> =
        rules.map { rule ->
            val predicateStates = rule.predicates.map { predicate ->
                evaluatePredicate(predicate, fieldValuesByDefinitionId, dispositionsByRequirementKey)
            }
            InformationRequestConditionEvaluationProjection(
                ruleKey = rule.ruleKey,
                expressionVersion = rule.expressionVersion,
                state = stateForAll(predicateStates),
                hiddenDataPolicy = rule.hiddenDataPolicy,
                sourceRequirementKeys = rule.predicates.mapNotNullTo(mutableSetOf()) { it.sourceRequirementKey },
                fieldDefinitionIds = rule.predicates.mapNotNullTo(mutableSetOf()) { it.fieldDefinitionId },
                occurrencePath = occurrencePath,
            )
        }

    private fun evaluatePredicate(
        predicate: InformationRequestTemplateConditionPredicateRequest,
        fieldValuesByDefinitionId: Map<UUID, CanonicalFieldValue?>,
        dispositionsByRequirementKey: Map<String, InformationRequestResponseDisposition?>,
    ): InformationRequestConditionEvaluationState =
        predicate.fieldDefinitionId?.let { fieldId ->
            if (!fieldValuesByDefinitionId.containsKey(fieldId))
                return InformationRequestConditionEvaluationState.UNKNOWN
            evaluateFieldPredicate(predicate, fieldValuesByDefinitionId[fieldId])
        } ?: predicate.sourceRequirementKey?.let { requirementKey ->
            if (!dispositionsByRequirementKey.containsKey(requirementKey))
                return InformationRequestConditionEvaluationState.UNKNOWN
            evaluateDispositionPredicate(predicate, dispositionsByRequirementKey[requirementKey])
        } ?: InformationRequestConditionEvaluationState.UNKNOWN

    private fun evaluateFieldPredicate(
        predicate: InformationRequestTemplateConditionPredicateRequest,
        stored: CanonicalFieldValue?,
    ): InformationRequestConditionEvaluationState
    {
        val type = predicate.valueType ?: return InformationRequestConditionEvaluationState.UNKNOWN
        val present = stored != null && !stored.isEmpty
        if (predicate.operator == FieldOperator.IS_EMPTY)
            return booleanState(!present)
        if (predicate.operator == FieldOperator.IS_NOT_EMPTY)
            return booleanState(present)
        if (!present || stored?.type != type)
            return InformationRequestConditionEvaluationState.FALSE

        return when (type)
        {
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT ->
                booleanState(matchesText(predicate.operator, stored.textValue, predicate.value))
            FieldValueType.BOOLEAN ->
                booleanState(matchesBoolean(predicate.operator, stored.boolValue, predicate.value))
            FieldValueType.INTEGER, FieldValueType.DECIMAL ->
                booleanState(matchesNumber(predicate.operator, stored.numberValue, predicate.value))
            FieldValueType.DATE ->
                booleanState(matchesDate(predicate.operator, stored.dateValue, predicate.value))
            FieldValueType.DATE_TIME ->
                booleanState(matchesDateTime(predicate.operator, stored.datetimeValue, predicate.value))
            FieldValueType.SINGLE_SELECT ->
                booleanState(matchesSingleSelect(predicate.operator, stored.selectionCodes.firstOrNull(), predicate.value))
            FieldValueType.MULTI_SELECT ->
                booleanState(matchesMultiSelect(predicate.operator, stored.selectionCodes, predicate.value))
        }
    }

    private fun evaluateDispositionPredicate(
        predicate: InformationRequestTemplateConditionPredicateRequest,
        disposition: InformationRequestResponseDisposition?,
    ): InformationRequestConditionEvaluationState
    {
        val present = disposition != null && disposition != InformationRequestResponseDisposition.NOT_ANSWERED
        return when (predicate.operator)
        {
            FieldOperator.IS_EMPTY -> booleanState(!present)
            FieldOperator.IS_NOT_EMPTY -> booleanState(present)
            FieldOperator.EQUALS -> booleanState(disposition == predicate.expectedDisposition)
            FieldOperator.NOT_EQUALS -> booleanState(disposition != predicate.expectedDisposition)
            else -> InformationRequestConditionEvaluationState.UNKNOWN
        }
    }

    private fun stateForAll(states: List<InformationRequestConditionEvaluationState>): InformationRequestConditionEvaluationState =
        when
        {
            InformationRequestConditionEvaluationState.FALSE in states -> InformationRequestConditionEvaluationState.FALSE
            InformationRequestConditionEvaluationState.UNKNOWN in states -> InformationRequestConditionEvaluationState.UNKNOWN
            else -> InformationRequestConditionEvaluationState.TRUE
        }

    private fun booleanState(value: Boolean): InformationRequestConditionEvaluationState =
        if (value) InformationRequestConditionEvaluationState.TRUE else InformationRequestConditionEvaluationState.FALSE

    private fun matchesText(operator: FieldOperator, stored: String?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalString(literal) ?: return false
        return when (operator)
        {
            FieldOperator.EQUALS -> storedValue == expected
            FieldOperator.NOT_EQUALS -> storedValue != expected
            FieldOperator.CONTAINS -> storedValue.contains(expected)
            FieldOperator.STARTS_WITH -> storedValue.startsWith(expected)
            else -> false
        }
    }

    private fun matchesBoolean(operator: FieldOperator, stored: Boolean?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalBoolean(literal) ?: return false
        return when (operator)
        {
            FieldOperator.EQUALS -> storedValue == expected
            FieldOperator.NOT_EQUALS -> storedValue != expected
            else -> false
        }
    }

    private fun matchesNumber(operator: FieldOperator, stored: BigDecimal?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalBigDecimal(literal) ?: return false
        val comparison = storedValue.compareTo(expected)
        return compareByOperator(operator, comparison)
    }

    private fun matchesDate(operator: FieldOperator, stored: LocalDate?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalString(literal)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return false
        return compareByOperator(operator, storedValue.compareTo(expected))
    }

    private fun matchesDateTime(operator: FieldOperator, stored: Instant?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalString(literal)?.let { CanonicalDateTime.parse(it)?.instant } ?: return false
        return compareByOperator(operator, storedValue.compareTo(expected))
    }

    private fun matchesSingleSelect(operator: FieldOperator, stored: String?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        return when (operator)
        {
            FieldOperator.EQUALS -> storedValue == literalString(literal)
            FieldOperator.NOT_EQUALS -> storedValue != literalString(literal)
            FieldOperator.IN -> literalStringList(literal)?.contains(storedValue) ?: false
            FieldOperator.NOT_IN -> literalStringList(literal)?.let { storedValue !in it } ?: false
            else -> false
        }
    }

    private fun matchesMultiSelect(operator: FieldOperator, stored: List<String>, literal: JsonElement?): Boolean
    {
        val storedValues = stored.toSet()
        return when (operator)
        {
            FieldOperator.CONTAINS -> literalString(literal)?.let { it in storedValues } ?: false
            FieldOperator.IN -> literalStringList(literal)?.any { it in storedValues } ?: false
            FieldOperator.NOT_IN -> literalStringList(literal)?.none { it in storedValues } ?: false
            else -> false
        }
    }

    private fun compareByOperator(operator: FieldOperator, comparison: Int): Boolean =
        when (operator)
        {
            FieldOperator.EQUALS -> comparison == 0
            FieldOperator.NOT_EQUALS -> comparison != 0
            FieldOperator.LESS_THAN -> comparison < 0
            FieldOperator.LESS_THAN_OR_EQUAL -> comparison <= 0
            FieldOperator.GREATER_THAN -> comparison > 0
            FieldOperator.GREATER_THAN_OR_EQUAL -> comparison >= 0
            else -> false
        }

    private fun literalString(literal: JsonElement?): String? =
        (literal as? JsonPrimitive)?.content

    private fun literalBoolean(literal: JsonElement?): Boolean? =
        (literal as? JsonPrimitive)?.booleanOrNull

    private fun literalBigDecimal(literal: JsonElement?): BigDecimal? =
        (literal as? JsonPrimitive)?.content?.trim()?.let { runCatching { BigDecimal(it) }.getOrNull() }

    private fun literalStringList(literal: JsonElement?): List<String>? =
        (literal as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
}
