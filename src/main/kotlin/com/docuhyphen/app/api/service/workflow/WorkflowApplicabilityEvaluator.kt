package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.workflow.WorkflowRequirementOperand
import com.docuhyphen.app.api.model.workflow.WorkflowTriggerSubject
import com.docuhyphen.app.api.service.fields.CanonicalFieldValue
import com.docuhyphen.app.api.service.fields.ExchangeFieldQueryService
import com.docuhyphen.app.api.service.fields.FieldOperator
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Evaluates a workflow definition's [ApplicabilitySpec] against the subject Exchange's typed field
 * values. AND semantics: every condition must match. Missing data (no schema, no value, retired
 * option, unevaluatable literal, unsupported operator) is always a non-match; the gate never
 * fails open. Conditions reference the immutable `fieldDefinitionId` and option codes, never labels.
 */
@ApplicationScoped
class WorkflowApplicabilityEvaluator @Inject constructor(
    private val exchangeFieldQueryService: ExchangeFieldQueryService,
    private val typeRegistry: FieldTypeRegistry,
    private val operandSource: WorkflowRequirementOperandSource,
)
{
    private val logger = LoggerFactory.getLogger(WorkflowApplicabilityEvaluator::class.java)

    /**
     * @return true if the definition should start for this subject. A null/empty [applicability]
     *   block is always applicable. Only EXCHANGE subjects support field conditions.
     */
    fun isApplicable(
        subjectResourceType: String?,
        subjectResourceId: UUID?,
        @Suppress("UNUSED_PARAMETER") organizationId: UUID?,
        applicability: ApplicabilitySpec?,
        subjectData: Map<String, String> = emptyMap(),
    ): Boolean
    {
        val fieldConditions = applicability?.fieldConditions.orEmpty()
        val requirementConditions = applicability?.requirementConditions.orEmpty()
        if (fieldConditions.isEmpty() && requirementConditions.isEmpty()) return true
        if (fieldConditions.isNotEmpty() && !fieldConditionsMatch(subjectResourceType, subjectResourceId, fieldConditions)) return false
        return requirementConditions.isEmpty() ||
            requirementConditionsMatch(subjectResourceType, subjectResourceId, subjectData, requirementConditions)
    }

    fun validate(applicability: ApplicabilitySpec?, trigger: WorkflowTriggerSubject? = null)
    {
        val conditions = applicability?.fieldConditions.orEmpty()
        if (conditions.isNotEmpty() && trigger?.subjectResourceType == INFORMATION_REQUEST_SUBJECT)
            throw IllegalArgumentException("Applicability: Exchange field conditions apply only to Exchange triggers")
        for (condition in conditions)
        {
            runCatching { UUID.fromString(condition.fieldDefinitionId) }.getOrElse {
                throw IllegalArgumentException("Applicability: invalid fieldDefinitionId '${condition.fieldDefinitionId}'")
            }
            validateOperator(condition.valueType, condition.operator, condition.value)
        }
        val requirementConditions = applicability?.requirementConditions.orEmpty()
        if (requirementConditions.isEmpty()) return
        if (trigger == null || trigger.subjectResourceType != INFORMATION_REQUEST_SUBJECT ||
            PACKAGE_FIELD !in trigger.subjectFieldNames)
            throw IllegalArgumentException(
                "Applicability: requirement conditions apply only to Information Request triggers that name a Submission Package",
            )
        for (condition in requirementConditions)
        {
            runCatching { UUID.fromString(condition.templateRequirementId) }.getOrElse {
                throw IllegalArgumentException("Applicability: invalid templateRequirementId '${condition.templateRequirementId}'")
            }
            if (condition.occurrencePath.isBlank())
                throw IllegalArgumentException("Applicability: a requirement condition names its occurrence path")
            validateOperator(condition.valueType, condition.operator, condition.value)
        }
    }

    private fun fieldConditionsMatch(
        subjectResourceType: String?,
        subjectResourceId: UUID?,
        conditions: List<FieldConditionSpec>,
    ): Boolean
    {
        if (subjectResourceType != ResourceType.EXCHANGE.name || subjectResourceId == null)
        {
            logger.debug("Applicability conditions present but subject is not an Exchange; non-match")
            return false
        }

        val snapshot = exchangeFieldQueryService.getCanonicalValues(subjectResourceId)
        if (!snapshot.hasAssignedSchema)
        {
            logger.debug("Exchange {} has no assigned schema; applicability non-match", subjectResourceId)
            return false
        }

        return conditions.all { condition ->
            val fieldDefinitionId = runCatching { UUID.fromString(condition.fieldDefinitionId) }.getOrNull()
            if (fieldDefinitionId == null)
            {
                logger.warn("Applicability condition has invalid fieldDefinitionId {}; non-match", condition.fieldDefinitionId)
                return@all false
            }
            evaluate(condition.valueType, condition.operator, condition.value, snapshot.valuesByFieldDefinitionId[fieldDefinitionId])
        }
    }

    private fun requirementConditionsMatch(
        subjectResourceType: String?,
        subjectResourceId: UUID?,
        subjectData: Map<String, String>,
        conditions: List<RequirementConditionSpec>,
    ): Boolean
    {
        if (subjectResourceType != INFORMATION_REQUEST_SUBJECT || subjectResourceId == null) return false
        val packageId = subjectData[PACKAGE_FIELD]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        return conditions.all { condition ->
            val templateRequirementId = runCatching { UUID.fromString(condition.templateRequirementId) }.getOrNull()
                ?: return@all false
            when (val operand = operandSource.frozenValue(subjectResourceId, packageId, templateRequirementId, condition.occurrencePath))
            {
                is WorkflowRequirementOperand.Unavailable -> false
                WorkflowRequirementOperand.Empty -> evaluate(condition.valueType, condition.operator, condition.value, null)
                is WorkflowRequirementOperand.Value ->
                    operand.value.type == condition.valueType &&
                        evaluate(condition.valueType, condition.operator, condition.value, operand.value)
            }
        }
    }

    private fun validateOperator(valueType: FieldValueType, operator: FieldOperator, value: JsonElement?)
    {
        val supported = typeRegistry.contractFor(valueType).supportedOperators
        if (operator !in supported)
            throw IllegalArgumentException("Applicability: operator $operator is not valid for field type $valueType")
        if (requiresValue(operator) && (value == null || value is JsonNull))
            throw IllegalArgumentException("Applicability: a value is required for operator $operator")
    }

    private fun evaluate(
        valueType: FieldValueType,
        operator: FieldOperator,
        literal: JsonElement?,
        stored: CanonicalFieldValue?,
    ): Boolean
    {
        val supported = typeRegistry.contractFor(valueType).supportedOperators
        if (operator !in supported)
        {
            logger.warn("Operator {} unsupported for type {}; non-match", operator, valueType)
            return false
        }

        val present = stored != null && !stored.isEmpty
        if (operator == FieldOperator.IS_EMPTY) return !present
        if (operator == FieldOperator.IS_NOT_EMPTY) return present
        if (!present) return false

        return when (valueType)
        {
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT ->
                matchesText(operator, stored!!.textValue, literal)
            FieldValueType.BOOLEAN ->
                matchesBoolean(operator, stored!!.boolValue, literal)
            FieldValueType.INTEGER, FieldValueType.DECIMAL ->
                matchesNumber(operator, stored!!.numberValue, literal)
            FieldValueType.DATE ->
                matchesDate(operator, stored!!.dateValue, literal)
            FieldValueType.DATE_TIME ->
                matchesDateTime(operator, stored!!.datetimeValue, literal)
            FieldValueType.SINGLE_SELECT ->
                matchesSingleSelect(operator, stored!!.selectionCodes.firstOrNull(), literal)
            FieldValueType.MULTI_SELECT ->
                matchesMultiSelect(operator, stored!!.selectionCodes, literal)
        }
    }

    // ── Per-type comparisons ──────────────────────────────────────────────────

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
        val cmp = storedValue.compareTo(expected)
        return when (operator)
        {
            FieldOperator.EQUALS -> cmp == 0
            FieldOperator.NOT_EQUALS -> cmp != 0
            FieldOperator.LESS_THAN -> cmp < 0
            FieldOperator.LESS_THAN_OR_EQUAL -> cmp <= 0
            FieldOperator.GREATER_THAN -> cmp > 0
            FieldOperator.GREATER_THAN_OR_EQUAL -> cmp >= 0
            else -> false
        }
    }

    private fun matchesDate(operator: FieldOperator, stored: LocalDate?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalString(literal)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return warnUnparseable(literal)
        val cmp = storedValue.compareTo(expected)
        return compareByOperator(operator, cmp)
    }

    private fun matchesDateTime(operator: FieldOperator, stored: Instant?, literal: JsonElement?): Boolean
    {
        val storedValue = stored ?: return false
        val expected = literalString(literal)?.let { parseDateTime(it) } ?: return warnUnparseable(literal)
        val cmp = storedValue.compareTo(expected)
        return compareByOperator(operator, cmp)
    }

    private fun matchesSingleSelect(operator: FieldOperator, stored: String?, literal: JsonElement?): Boolean
    {
        val storedCode = stored ?: return false
        return when (operator)
        {
            FieldOperator.EQUALS -> storedCode == literalString(literal)
            FieldOperator.NOT_EQUALS -> storedCode != literalString(literal)
            FieldOperator.IN -> literalStringList(literal)?.contains(storedCode) ?: false
            FieldOperator.NOT_IN -> literalStringList(literal)?.let { !it.contains(storedCode) } ?: false
            else -> false
        }
    }

    private fun matchesMultiSelect(operator: FieldOperator, stored: List<String>, literal: JsonElement?): Boolean
    {
        val storedCodes = stored.toSet()
        return when (operator)
        {
            FieldOperator.CONTAINS -> literalString(literal)?.let { storedCodes.contains(it) } ?: false
            FieldOperator.IN -> literalStringList(literal)?.any { storedCodes.contains(it) } ?: false
            FieldOperator.NOT_IN -> literalStringList(literal)?.none { storedCodes.contains(it) } ?: false
            else -> false
        }
    }

    private fun compareByOperator(operator: FieldOperator, cmp: Int): Boolean = when (operator)
    {
        FieldOperator.EQUALS -> cmp == 0
        FieldOperator.NOT_EQUALS -> cmp != 0
        FieldOperator.LESS_THAN -> cmp < 0
        FieldOperator.LESS_THAN_OR_EQUAL -> cmp <= 0
        FieldOperator.GREATER_THAN -> cmp > 0
        FieldOperator.GREATER_THAN_OR_EQUAL -> cmp >= 0
        else -> false
    }

    // ── Literal parsing (canonical, never display strings) ─────────────────────

    private fun requiresValue(operator: FieldOperator): Boolean =
        operator != FieldOperator.IS_EMPTY && operator != FieldOperator.IS_NOT_EMPTY

    private fun literalString(literal: JsonElement?): String? =
        (literal as? JsonPrimitive)?.content

    private fun literalBoolean(literal: JsonElement?): Boolean?
    {
        val prim = literal as? JsonPrimitive ?: return null
        prim.booleanOrNull?.let { return it }
        return when (prim.content.trim().lowercase())
        {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    }

    private fun literalBigDecimal(literal: JsonElement?): BigDecimal?
    {
        val content = (literal as? JsonPrimitive)?.content?.trim() ?: return null
        return runCatching { BigDecimal(content) }.getOrElse { warnUnparseable(literal); null }
    }

    private fun literalStringList(literal: JsonElement?): List<String>? =
        (literal as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }

    private fun parseDateTime(raw: String): Instant? =
        com.docuhyphen.app.api.service.fields.CanonicalDateTime.parse(raw)?.instant

    private fun warnUnparseable(literal: JsonElement?): Boolean
    {
        logger.warn("Applicability literal {} could not be parsed; non-match", literal)
        return false
    }

    private companion object
    {
        const val INFORMATION_REQUEST_SUBJECT = "INFORMATION_REQUEST"
        const val PACKAGE_FIELD = "submissionPackageId"
    }
}
