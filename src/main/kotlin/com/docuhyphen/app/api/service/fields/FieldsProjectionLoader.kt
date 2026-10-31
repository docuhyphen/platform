package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.fields.ResolvedFieldBinding
import com.docuhyphen.app.api.model.fields.ResolvedFieldValue
import com.docuhyphen.app.api.repository.fields.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class FieldsProjectionLoader @Inject constructor(
    private val bindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val fieldDefinitionRepository: FieldDefinitionRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val selectionRepository: FieldValueSelectionRepository,
)
{
    fun resolveBindings(
        schemaVersionId: UUID,
        include: (SchemaFieldBinding) -> Boolean = { true },
    ): List<ResolvedFieldBinding>
    {
        val bindings = bindingRepository.findByVersion(schemaVersionId).filter(include)
        if (bindings.isEmpty()) return emptyList()
        val contracts = fieldContractRepository.findByIds(bindings.map { it.fieldContractId })
            .associateBy { it.id }
        val definitions = contracts.values
            .mapNotNull { fieldDefinitionRepository.findById(it.fieldDefinitionId) }
            .associateBy { it.id }

        return bindings.sortedBy { it.displayOrder }.mapNotNull { binding ->
            val contract = contracts[binding.fieldContractId] ?: return@mapNotNull null
            val definition = definitions[contract.fieldDefinitionId] ?: return@mapNotNull null
            ResolvedFieldBinding(binding, contract, definition)
        }
    }

    fun resolveValues(
        schemaVersionId: UUID,
        valueSet: FieldValueSet?,
        include: (SchemaFieldBinding) -> Boolean = { true },
    ): List<ResolvedFieldValue>
    {
        val questions = resolveBindings(schemaVersionId, include)
        if (questions.isEmpty()) return emptyList()
        val storedByContract = valueSet
            ?.let { fieldValueRepository.findByValueSet(it.id) }
            .orEmpty()
            .associateBy { it.fieldContractId }

        return questions.map { question ->
            val stored = storedByContract[question.contract.id]
            ResolvedFieldValue(question, stored, selectionCodesOf(stored))
        }
    }

    private fun selectionCodesOf(stored: FieldValue?): List<String>
    {
        if (stored == null || !holdsSelections(stored.valueType)) return emptyList()
        return selectionRepository.findByValue(stored.id).map { it.optionCode }
    }

    private fun holdsSelections(valueType: FieldValueType): Boolean =
        valueType == FieldValueType.SINGLE_SELECT || valueType == FieldValueType.MULTI_SELECT
}
