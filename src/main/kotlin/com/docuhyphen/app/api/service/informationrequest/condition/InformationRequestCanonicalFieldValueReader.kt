package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestCanonicalFieldValueReader @Inject constructor(
    private val fieldsReader: FieldsReadFacade,
) {
    fun valuesByOccurrencePath(
        requestId: UUID,
        occurrencePaths: Set<String>,
    ): Map<String, Map<UUID, CanonicalFieldValue?>> {
        val assignment = fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId))
            ?: return emptyMap()
        return occurrencePaths.associateWith { occurrencePath ->
            val valueSet = if (InformationRequestOccurrencePath.isRoot(occurrencePath))
                fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Root)
            else
                fieldsReader.valueSetOf(assignment.id, FieldValueSetRef.Occurrence(occurrencePath))
            valueSet?.let { canonicalValues(it.id) }.orEmpty()
        }
    }

    private fun canonicalValues(valueSetId: UUID): Map<UUID, CanonicalFieldValue?> {
        val values = fieldsReader.valuesIn(valueSetId)
        if (values.isEmpty()) return emptyMap()
        val fieldDefinitionIdByContractId = fieldsReader
            .contracts(values.map { it.fieldContractId }.distinct())
            .associate { it.id to it.fieldDefinitionId }
        return values.mapNotNull { value ->
            fieldDefinitionIdByContractId[value.fieldContractId]?.let { fieldDefinitionId ->
                fieldDefinitionId to CanonicalValueCodec.toCanonical(value, selectionCodes(value))
            }
        }.toMap()
    }

    private fun selectionCodes(value: FieldValue): List<String> =
        if (value.valueType == FieldValueType.SINGLE_SELECT || value.valueType == FieldValueType.MULTI_SELECT)
            fieldsReader.selectionCodesOf(value.id)
        else
            emptyList()
}
