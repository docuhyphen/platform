package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FieldTypeRegistry
{
    private val byType: Map<FieldValueType, FieldTypeContract> =
        ALL_FIELD_TYPE_CONTRACTS.associateBy { it.type }

    fun contractFor(type: FieldValueType): FieldTypeContract =
        byType[type] ?: throw FieldValidationException("Unsupported field type: $type")

    fun supportedTypes(): Set<FieldValueType> = byType.keys
}
