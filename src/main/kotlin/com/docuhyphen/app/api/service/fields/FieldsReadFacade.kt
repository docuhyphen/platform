package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.repository.fields.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class FieldsReadFacade @Inject constructor(
    private val assignmentRepository: SchemaAssignmentRepository,
    private val fieldValueSetRepository: FieldValueSetRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val selectionRepository: FieldValueSelectionRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val schemaFieldBindingRepository: SchemaFieldBindingRepository,
    private val fieldDefinitionRepository: FieldDefinitionRepository,
) {
    fun existingFieldDefinitionIds(fieldDefinitionIds: Collection<UUID>): Set<UUID> =
        if (fieldDefinitionIds.isEmpty()) emptySet() else fieldDefinitionRepository.findExistingIds(fieldDefinitionIds)

    fun assignmentFor(resource: FieldsResourceRef): SchemaAssignment? =
        assignmentRepository.findByResource(resource.resourceType, resource.resourceId)

    fun valueSetOf(assignmentId: UUID, valueSet: FieldValueSetRef): FieldValueSet? = when (valueSet) {
        FieldValueSetRef.Root -> fieldValueSetRepository.findRoot(assignmentId)
        is FieldValueSetRef.Occurrence -> fieldValueSetRepository.findOccurrence(assignmentId, valueSet.occurrencePath)
    }

    fun valuesIn(valueSetId: UUID): List<FieldValue> = fieldValueRepository.findByValueSet(valueSetId)

    fun selectionCodesOf(valueId: UUID): List<String> = selectionRepository.findByValue(valueId).map { it.optionCode }

    fun contract(fieldContractId: UUID): FieldContract? = fieldContractRepository.findById(fieldContractId)

    fun contracts(fieldContractIds: Collection<UUID>): List<FieldContract> =
        fieldContractRepository.findByIds(fieldContractIds)

    fun schemaBindings(schemaVersionId: UUID): List<SchemaFieldBinding> =
        schemaFieldBindingRepository.findByVersion(schemaVersionId)
}
