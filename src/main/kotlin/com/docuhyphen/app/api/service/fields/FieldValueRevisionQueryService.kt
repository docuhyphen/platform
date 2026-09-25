package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionSelectionRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class FieldValueRevisionQueryService @Inject constructor(
    private val assignmentRepository: SchemaAssignmentRepository,
    private val bindingRepository: SchemaFieldBindingRepository,
    private val revisionRepository: FieldValueRevisionRepository,
    private val selectionRepository: FieldValueRevisionSelectionRepository,
)
{
    fun latestRevision(resource: FieldsResourceRef, valueSetId: UUID, fieldDefinitionId: UUID): FieldValueRevision?
    {
        val assignment = assignmentRepository.findByResource(resource.resourceType, resource.resourceId) ?: return null
        val binding = bindingRepository.findByVersion(assignment.schemaVersionId)
            .firstOrNull { it.fieldDefinitionId == fieldDefinitionId }
            ?: return null
        return revisionRepository.findLatest(valueSetId, binding.fieldContractId)
    }

    fun valueOf(revisionId: UUID): FieldValueRevisionValue?
    {
        val revision = revisionRepository.findById(revisionId) ?: return null
        val codes = selectionRepository.findByRevision(revision.id).map { it.optionCode }
        val value = FieldValue().apply {
            valueType = revision.valueType
            textValue = revision.textValue
            numberValue = revision.numberValue
            boolValue = revision.boolValue
            dateValue = revision.dateValue
            datetimeValue = revision.datetimeValue
            datetimeOffsetMinutes = revision.datetimeOffsetMinutes
        }
        return FieldValueRevisionValue(
            revisionId = revision.id,
            fieldContractId = revision.fieldContractId,
            valueType = revision.valueType,
            cleared = revision.isCleared,
            value = CanonicalValueCodec.toJson(value, codes),
        )
    }
}
