package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.fields.FieldOption
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTemplateConditionContractValidatorTest {
    private val schemaVersionId = UUID.randomUUID()
    private val textField = UUID.randomUUID()
    private val choiceField = UUID.randomUUID()
    private val unboundField = UUID.randomUUID()
    private val fields = mock<FieldsReadFacade>()
    private val validator = InformationRequestTemplateConditionContractValidator(fields)

    init {
        val textContract = contract(textField, FieldValueType.SHORT_TEXT)
        val choiceContract = contract(choiceField, FieldValueType.SINGLE_SELECT).apply {
            optionsJson = FieldOption.encodeList(
                listOf(
                    FieldOption("first-option", "First option"),
                    FieldOption("second-option", "Second option"),
                    FieldOption("retired-option", "Retired option", active = false),
                ),
            )
        }
        whenever(fields.schemaBindings(schemaVersionId)).thenReturn(
            listOf(binding(textContract), binding(choiceContract)),
        )
        whenever(fields.contracts(any())).thenReturn(listOf(textContract, choiceContract))
    }

    @Test
    fun `a predicate typed unlike the Field its Schema Version pins is refused and names the rule`() {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.requireMatchesSchema(
                schemaVersionId,
                rules(predicate(textField, FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive(true))),
            )
        }

        assertEquals(RULE, refusal.conditionRuleKey)
        assertTrue(refusal.message.contains(FieldValueType.SHORT_TEXT.name), refusal.message)
    }

    @Test
    fun `an option code the pinned Field does not offer is refused for single and membership comparisons`() {
        listOf(
            predicate(choiceField, FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive("unknown-option")),
            predicate(
                choiceField,
                FieldValueType.SINGLE_SELECT,
                FieldOperator.NOT_EQUALS,
                JsonPrimitive("retired-option")
            ),
            predicate(
                choiceField,
                FieldValueType.SINGLE_SELECT,
                FieldOperator.IN,
                JsonArray(listOf(JsonPrimitive("first-option"), JsonPrimitive("unknown-option"))),
            ),
        ).forEach { predicate ->
            val refusal = assertThrows<InformationRequestTemplateValidationException>(predicate.toString()) {
                validator.requireMatchesSchema(schemaVersionId, rules(predicate))
            }
            assertEquals(RULE, refusal.conditionRuleKey, refusal.message)
        }
    }

    @Test
    fun `a predicate that matches its pinned Field passes and a Field the Schema does not bind is left to publication`() {
        assertDoesNotThrow {
            validator.requireMatchesSchema(
                schemaVersionId,
                rules(
                    predicate(
                        textField,
                        FieldValueType.SHORT_TEXT,
                        FieldOperator.EQUALS,
                        JsonPrimitive("recorded value")
                    ),
                    predicate(
                        choiceField,
                        FieldValueType.SINGLE_SELECT,
                        FieldOperator.NOT_IN,
                        JsonArray(listOf(JsonPrimitive("first-option"), JsonPrimitive("second-option"))),
                    ),
                    predicate(unboundField, FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive(true)),
                    predicate(textField, FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY, null),
                ),
            )
        }
    }

    @Test
    fun `a draft that names no Schema Version yet is not checked against one`() {
        assertDoesNotThrow {
            validator.requireMatchesSchema(
                null,
                rules(predicate(textField, FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive(true))),
            )
        }
        verify(fields, never()).schemaBindings(any())
    }

    private fun rules(vararg predicates: InformationRequestTemplateConditionPredicateRequest) =
        listOf(InformationRequestTemplateConditionRuleRequest(ruleKey = RULE, predicates = predicates.toList()))

    private fun predicate(field: UUID, type: FieldValueType, operator: FieldOperator, value: JsonElement?) =
        InformationRequestTemplateConditionPredicateRequest(
            fieldDefinitionId = field,
            valueType = type,
            operator = operator,
            value = value,
        )

    private fun contract(field: UUID, type: FieldValueType) = FieldContract().apply {
        fieldDefinitionId = field
        valueType = type
        label = "Recorded control"
    }

    private fun binding(contract: FieldContract) = SchemaFieldBinding().apply {
        schemaVersionId = this@InformationRequestTemplateConditionContractValidatorTest.schemaVersionId
        fieldContractId = contract.id
        fieldDefinitionId = contract.fieldDefinitionId
    }

    private companion object {
        const val RULE = "control-applies"
    }
}
