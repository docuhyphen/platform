package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.fields.FieldConstraints
import com.docuhyphen.app.api.model.fields.ResolvedFieldValue
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

object FieldValueDtoMapper
{
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
