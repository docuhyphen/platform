package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.service.fields.CanonicalFieldValue
import com.docuhyphen.app.api.service.fields.FieldOperator
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class InformationRequestConditionEvaluatorTest
{
    private val evaluator = InformationRequestConditionEvaluator()
    private val fieldId = UUID.randomUUID()

    @Test
    fun `a field predicate evaluates true and projects only safe dependency identity`()
    {
        val rule = rule(
            predicate = fieldPredicate(
                operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                value = JsonPrimitive("10"),
                valueType = FieldValueType.DECIMAL,
            ),
        )

        val projection = evaluator.evaluate(
            listOf(rule),
            fieldValuesByDefinitionId = mapOf(
                fieldId to CanonicalFieldValue(
                    type = FieldValueType.DECIMAL,
                    isEmpty = false,
                    numberValue = BigDecimal("10.00"),
                ),
            ),
        ).single()

        assertEquals(InformationRequestConditionEvaluationState.TRUE, projection.state)
        assertEquals(setOf(fieldId), projection.fieldDefinitionIds)
        assertEquals(emptySet<String>(), projection.sourceRequirementKeys)
    }

    @Test
    fun `a changed answer deterministically changes the same rule result`()
    {
        val rule = rule(
            predicate = fieldPredicate(
                operator = FieldOperator.EQUALS,
                value = JsonPrimitive(true),
                valueType = FieldValueType.BOOLEAN,
            ),
        )

        val falseProjection = evaluator.evaluate(
            listOf(rule),
            fieldValuesByDefinitionId = mapOf(
                fieldId to CanonicalFieldValue(
                    type = FieldValueType.BOOLEAN,
                    isEmpty = false,
                    boolValue = false,
                ),
            ),
        ).single()
        val trueProjection = evaluator.evaluate(
            listOf(rule),
            fieldValuesByDefinitionId = mapOf(
                fieldId to CanonicalFieldValue(
                    type = FieldValueType.BOOLEAN,
                    isEmpty = false,
                    boolValue = true,
                ),
            ),
        ).single()

        assertEquals(InformationRequestConditionEvaluationState.FALSE, falseProjection.state)
        assertEquals(InformationRequestConditionEvaluationState.TRUE, trueProjection.state)
    }

    @Test
    fun `a missing source is unknown rather than false`()
    {
        val projection = evaluator.evaluate(listOf(rule(predicate = fieldPredicate()))).single()

        assertEquals(InformationRequestConditionEvaluationState.UNKNOWN, projection.state)
    }

    @Test
    fun `a null field value has defined empty semantics`()
    {
        val emptyRule = rule(
            ruleKey = "when-recorded-note-empty",
            predicate = fieldPredicate(operator = FieldOperator.IS_EMPTY),
        )
        val notEmptyRule = rule(
            ruleKey = "when-recorded-note-present",
            predicate = fieldPredicate(operator = FieldOperator.IS_NOT_EMPTY),
        )

        val projections = evaluator.evaluate(
            listOf(emptyRule, notEmptyRule),
            fieldValuesByDefinitionId = mapOf(fieldId to CanonicalFieldValue.empty(FieldValueType.SHORT_TEXT)),
        ).associateBy { it.ruleKey }

        assertEquals(
            InformationRequestConditionEvaluationState.TRUE,
            projections.getValue("when-recorded-note-empty").state,
        )
        assertEquals(
            InformationRequestConditionEvaluationState.FALSE,
            projections.getValue("when-recorded-note-present").state,
        )
    }

    @Test
    fun `a requirement disposition predicate evaluates without exposing the disposition value`()
    {
        val rule = rule(
            predicate = InformationRequestTemplateConditionPredicateRequest(
                sourceRequirementKey = "recorded-note",
                operator = FieldOperator.EQUALS,
                expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
            ),
        )

        val projection = evaluator.evaluate(
            listOf(rule),
            dispositionsByRequirementKey = mapOf("recorded-note" to InformationRequestResponseDisposition.PROVIDED),
        ).single()

        assertEquals(InformationRequestConditionEvaluationState.TRUE, projection.state)
        assertEquals(setOf("recorded-note"), projection.sourceRequirementKeys)
        assertEquals(emptySet<UUID>(), projection.fieldDefinitionIds)
    }

    private fun rule(
        ruleKey: String = "when-recorded-note-applies",
        predicate: InformationRequestTemplateConditionPredicateRequest,
    ) = InformationRequestTemplateConditionRuleRequest(
        ruleKey = ruleKey,
        predicates = listOf(predicate),
    )

    private fun fieldPredicate(
        operator: FieldOperator = FieldOperator.EQUALS,
        value: kotlinx.serialization.json.JsonElement? = JsonPrimitive("ready"),
        valueType: FieldValueType = FieldValueType.SHORT_TEXT,
    ) = InformationRequestTemplateConditionPredicateRequest(
        fieldDefinitionId = fieldId,
        valueType = valueType,
        operator = operator,
        value = value,
    )
}
