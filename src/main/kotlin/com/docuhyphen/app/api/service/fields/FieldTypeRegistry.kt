package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import jakarta.enterprise.context.ApplicationScoped

/**
 * The controlled type registry. Resolves a [FieldTypeContract] for a [FieldValueType] and fails
 * closed for any unregistered type. Customer-provided executable code never runs here; the set of
 * contracts is fixed in [ALL_FIELD_TYPE_CONTRACTS]. See FIELDS-FEATURE.md "Type Contract".
 */
@ApplicationScoped
class FieldTypeRegistry
{
    private val byType: Map<FieldValueType, FieldTypeContract> =
        ALL_FIELD_TYPE_CONTRACTS.associateBy { it.type }

    /** @throws FieldValidationException when no contract is registered for [type] (fail closed). */
    fun contractFor(type: FieldValueType): FieldTypeContract =
        byType[type] ?: throw FieldValidationException("Unsupported field type: $type")

    fun supportedTypes(): Set<FieldValueType> = byType.keys
}
