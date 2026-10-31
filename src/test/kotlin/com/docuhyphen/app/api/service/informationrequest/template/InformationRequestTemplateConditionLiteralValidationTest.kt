package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.fields.FieldOperator
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class InformationRequestTemplateConditionLiteralValidationTest {
    private val validator = InformationRequestTemplateConfigurationValidator()
    private val field = UUID.nameUUIDFromBytes("recorded-control".toByteArray())

    @Test
    fun `a literal that its declared type cannot read is refused and names the rule`() {
        listOf(
            Triple(FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive("unparseable")),
            Triple(FieldValueType.BOOLEAN, FieldOperator.NOT_EQUALS, JsonPrimitive(1)),
            Triple(FieldValueType.INTEGER, FieldOperator.GREATER_THAN, JsonPrimitive("1.5")),
            Triple(FieldValueType.INTEGER, FieldOperator.EQUALS, JsonPrimitive("twelve")),
            Triple(FieldValueType.DECIMAL, FieldOperator.LESS_THAN, JsonPrimitive("1,5")),
            Triple(FieldValueType.DECIMAL, FieldOperator.LESS_THAN, JsonPrimitive("1".repeat(29))),
            Triple(FieldValueType.DATE, FieldOperator.EQUALS, JsonPrimitive("2026-02-30")),
            Triple(FieldValueType.DATE_TIME, FieldOperator.GREATER_THAN, JsonPrimitive("yesterday")),
            Triple(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("   ")),
            Triple(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive(" ")),
        ).forEach { (type, operator, literal) ->
            val refusal = assertThrows<InformationRequestTemplateValidationException>("$type $operator $literal") {
                validator.normalize(document(type, operator, literal))
            }
            assertEquals(RULE, refusal.conditionRuleKey, refusal.message)
        }
    }

    @Test
    fun `a scalar comparison takes one value and a membership comparison takes a list of values`() {
        listOf(
            Triple(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonArray(listOf(JsonPrimitive("recorded-value")))),
            Triple(FieldValueType.SHORT_TEXT, FieldOperator.CONTAINS, JsonObject(mapOf("text" to JsonPrimitive("a")))),
            Triple(FieldValueType.SINGLE_SELECT, FieldOperator.IN, JsonPrimitive("first-option")),
            Triple(FieldValueType.SINGLE_SELECT, FieldOperator.NOT_IN, JsonArray(emptyList())),
            Triple(FieldValueType.MULTI_SELECT, FieldOperator.IN, JsonArray(listOf(JsonNull))),
            Triple(
                FieldValueType.MULTI_SELECT,
                FieldOperator.IN,
                JsonArray(listOf(JsonArray(listOf(JsonPrimitive("a")))))
            ),
            Triple(FieldValueType.MULTI_SELECT, FieldOperator.NOT_IN, JsonArray(listOf(JsonPrimitive(" ")))),
            Triple(FieldValueType.SHORT_TEXT, FieldOperator.IS_EMPTY, JsonPrimitive("recorded-value")),
        ).forEach { (type, operator, literal) ->
            val refusal = assertThrows<InformationRequestTemplateValidationException>("$type $operator $literal") {
                validator.normalize(document(type, operator, literal))
            }
            assertEquals(RULE, refusal.conditionRuleKey, refusal.message)
        }
    }

    @Test
    fun `an accepted literal is stored in the canonical form its type reads`() {
        listOf(
            Triple(FieldValueType.BOOLEAN, FieldOperator.EQUALS, JsonPrimitive("true")) to JsonPrimitive(true),
            Triple(FieldValueType.BOOLEAN, FieldOperator.NOT_EQUALS, JsonPrimitive(false)) to JsonPrimitive(false),
            Triple(FieldValueType.INTEGER, FieldOperator.EQUALS, JsonPrimitive(12)) to JsonPrimitive("12"),
            Triple(FieldValueType.INTEGER, FieldOperator.EQUALS, JsonPrimitive("12.000")) to JsonPrimitive("12"),
            Triple(FieldValueType.DECIMAL, FieldOperator.LESS_THAN, JsonPrimitive(" 1.50 ")) to JsonPrimitive("1.5"),
            Triple(
                FieldValueType.DATE,
                FieldOperator.EQUALS,
                JsonPrimitive(" 2026-02-28 ")
            ) to JsonPrimitive("2026-02-28"),
            Triple(FieldValueType.DATE_TIME, FieldOperator.GREATER_THAN, JsonPrimitive("2026-02-28T10:15:00+02:00")) to
                    JsonPrimitive("2026-02-28T10:15:00+02:00"),
            Triple(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, JsonPrimitive("  recorded value ")) to
                    JsonPrimitive("recorded value"),
            Triple(FieldValueType.LONG_TEXT, FieldOperator.STARTS_WITH, JsonPrimitive("Recorded ")) to
                    JsonPrimitive("Recorded "),
            Triple(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS, JsonPrimitive(" first-option ")) to
                    JsonPrimitive("first-option"),
            Triple(
                FieldValueType.MULTI_SELECT,
                FieldOperator.IN,
                JsonArray(
                    listOf(
                        JsonPrimitive(" first-option"),
                        JsonPrimitive("second-option"),
                        JsonPrimitive("first-option")
                    )
                ),
            ) to JsonArray(listOf(JsonPrimitive("first-option"), JsonPrimitive("second-option"))),
        ).forEach { (given, expected) ->
            val (type, operator, literal) = given
            assertEquals(expected, normalizedValue(type, operator, literal), "$type $operator $literal")
        }
        assertNull(normalizedValue(FieldValueType.SHORT_TEXT, FieldOperator.IS_NOT_EMPTY, JsonNull))
    }

    private fun normalizedValue(type: FieldValueType, operator: FieldOperator, literal: JsonElement): JsonElement? =
        validator.normalize(document(type, operator, literal)).conditionRules.single().predicates.single().value

    private fun document(type: FieldValueType, operator: FieldOperator, literal: JsonElement) =
        InformationRequestTemplateConfigurationRequest(
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "collected-data",
                    title = "Collected data",
                    requirements = listOf(
                        InformationRequestTemplateRequirementRequest(
                            requirementKey = "recorded-control",
                            requirementType = InformationRequestRequirementType.FIELD,
                            prompt = "Record the control",
                            collectedFieldDefinitionId = field,
                        ),
                    ),
                ),
            ),
            conditionRules = listOf(
                InformationRequestTemplateConditionRuleRequest(
                    ruleKey = RULE,
                    predicates = listOf(
                        InformationRequestTemplateConditionPredicateRequest(
                            fieldDefinitionId = field,
                            valueType = type,
                            operator = operator,
                            value = literal,
                        ),
                    ),
                ),
            ),
        )

    private companion object {
        const val RULE = "control-applies"
    }
}
