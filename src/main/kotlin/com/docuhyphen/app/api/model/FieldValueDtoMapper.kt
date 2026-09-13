package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import com.docuhyphen.app.api.service.fields.FieldConstraints
import com.docuhyphen.app.api.service.fields.ResolvedFieldValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Shapes one gathered question and its answer into the entry a consumer reads. Nothing here reaches
 * a repository: the records were gathered before the mapping began, so the shape of a response can
 * be exercised on its own.
 */
object FieldValueDtoMapper
{
    /**
     * The entry for one question. A question with no stored answer, and one whose stored answer says
     * nothing, both read as empty, so a consumer has one condition to test rather than two.
     */
    fun toDto(resolved: ResolvedFieldValue): FieldValueDto
    {
        val stored = resolved.stored
        return FieldValueDto(
            fieldContractId = resolved.question.contract.id,
            schemaFieldBindingId = resolved.question.binding.id,
            namespace = resolved.question.definition.namespace,
            fieldKey = resolved.question.definition.fieldKey,
            label = resolved.question.contract.label,
            valueType = resolved.question.contract.valueType,
            isEmpty = stored == null || CanonicalValueCodec.isEmpty(stored, resolved.selectionCodes),
            value = valueJson(resolved),
        )
    }

    private fun valueJson(resolved: ResolvedFieldValue): JsonElement
    {
        val stored = resolved.stored ?: return JsonNull
        val scale = FieldConstraints.parse(resolved.question.contract.constraintsJson).scale
        return CanonicalValueCodec.toJson(stored, resolved.selectionCodes, scale)
    }
}
