package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * One question a Schema Version asks, gathered from the three records that define it: the binding
 * that places it in the version, the field contract that fixes its type and wording, and the stable
 * field definition that gives it an identity outliving any one contract version.
 */
data class ResolvedFieldBinding(
    val binding: SchemaFieldBinding,
    val contract: FieldContract,
    val definition: FieldDefinition,
)

/**
 * One question together with the answer a particular set of answers holds against it. [stored] is
 * null where the question has never been answered in that set, and [selectionCodes] is empty for
 * everything but a stored select-typed answer.
 */
data class ResolvedFieldValue(
    val question: ResolvedFieldBinding,
    val stored: FieldValue?,
    val selectionCodes: List<String> = emptyList(),
)

/**
 * Gathers the records a Fields projection is built from. Everything that reaches a repository to
 * assemble a projection lives here, so the classes that shape those records into responses stay
 * pure and can be exercised without a database.
 *
 * A question whose contract or stable definition cannot be loaded is left out rather than reported
 * as a question with missing parts: a consumer can render only what it can identify, and a partial
 * question would be indistinguishable from one whose wording is genuinely blank.
 */
@ApplicationScoped
class FieldsProjectionLoader @Inject constructor(
    private val bindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val fieldDefinitionRepository: FieldDefinitionRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val selectionRepository: FieldValueSelectionRepository,
)
{
    /**
     * The questions a Schema Version asks, in the order it asks them. [include] narrows that list to
     * the questions one caller may be shown, and is consulted on the binding alone so a caller's
     * policy never has to know how a question is assembled.
     */
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

    /**
     * The questions a Schema Version asks paired with the answers [valueSet] holds for them. Only
     * the addressed set is read, so an answer belonging to a repetition is never reported as the
     * resource's own.
     */
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

    /**
     * The option codes a stored answer carries. Only a select-typed answer can hold any, so nothing
     * else pays for the lookup.
     */
    private fun selectionCodesOf(stored: FieldValue?): List<String>
    {
        if (stored == null || !holdsSelections(stored.valueType)) return emptyList()
        return selectionRepository.findByValue(stored.id).map { it.optionCode }
    }

    private fun holdsSelections(valueType: FieldValueType): Boolean =
        valueType == FieldValueType.SINGLE_SELECT || valueType == FieldValueType.MULTI_SELECT
}
