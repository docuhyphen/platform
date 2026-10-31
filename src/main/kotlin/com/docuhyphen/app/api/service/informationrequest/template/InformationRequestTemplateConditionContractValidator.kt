package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.fields.FieldOption
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuseConditionRule
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.*

@ApplicationScoped
class InformationRequestTemplateConditionContractValidator @Inject constructor(
    private val fields: FieldsReadFacade,
) {
    fun requireMatchesSchema(
        schemaVersionId: UUID?,
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
    ) {
        val named = schemaVersionId ?: return
        val fieldPredicates = conditionRules.flatMap { rule ->
            rule.predicates.filter { it.fieldDefinitionId != null }.map { rule.ruleKey to it }
        }
        if (fieldPredicates.isEmpty()) return
        val bindings = fields.schemaBindings(named).associateBy { it.fieldDefinitionId }
        val contracts = fields.contracts(bindings.values.map { it.fieldContractId }).associateBy { it.id }
        fieldPredicates.forEach { (ruleKey, predicate) ->
            val contract = bindings[predicate.fieldDefinitionId]?.let { contracts[it.fieldContractId] }
                ?: return@forEach
            if (contract.valueType != predicate.valueType) {
                refuseConditionRule(
                    "Condition rule $ruleKey compares Field ${predicate.fieldDefinitionId} as " +
                            "${predicate.valueType}, but its Schema Version collects it as ${contract.valueType}",
                    ruleKey,
                )
            }
            if (contract.valueType in SELECTION_TYPES) {
                val offered = FieldOption.parseList(contract.optionsJson).filter { it.active }.map { it.code }.toSet()
                optionCodesOf(predicate).firstOrNull { it !in offered }?.let { code ->
                    refuseConditionRule(
                        "Condition rule $ruleKey compares Field ${predicate.fieldDefinitionId} with option $code, " +
                                "which that Field does not offer",
                        ruleKey,
                    )
                }
            }
        }
    }

    fun requireValidStored(schemaVersionId: UUID?, version: InformationRequestTemplateVersionDto?) {
        val stored = InformationRequestTemplateConfigurationMapper.toRequest(version ?: return).conditionRules
        requireMatchesSchema(
            schemaVersionId,
            InformationRequestTemplateConditionValidator.normalizeConditionRules(stored),
        )
    }

    private fun optionCodesOf(predicate: InformationRequestTemplateConditionPredicateRequest): List<String> =
        when (val value = predicate.value) {
            is JsonArray -> value.mapNotNull { entry ->
                (entry as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            }

            is JsonNull, null -> emptyList()
            is JsonPrimitive -> listOf(value.content)
            else -> emptyList()
        }

    private companion object {
        val SELECTION_TYPES = setOf(FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT)
    }
}
