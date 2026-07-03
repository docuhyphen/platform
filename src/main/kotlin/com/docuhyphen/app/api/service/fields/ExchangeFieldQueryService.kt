package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.FieldValueRepository
import com.docuhyphen.app.api.repository.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.SchemaAssignmentRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Read-only projection of an Exchange's stored field values into a comparable canonical form keyed
 * by the stable [com.docuhyphen.app.api.model.entity.FieldDefinition] id. Consumers (e.g. the
 * workflow applicability evaluator) call this method instead of touching the Fields repositories,
 * per the backend service-to-service rule. Stored [FieldValue] rows are keyed by `fieldContractId`;
 * this resolves each to its stable `fieldDefinitionId` via the [FieldContract] and surfaces
 * selection option codes for SINGLE_SELECT / MULTI_SELECT.
 */
@ApplicationScoped
class ExchangeFieldQueryService @Inject constructor(
    private val assignmentRepository: SchemaAssignmentRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val selectionRepository: FieldValueSelectionRepository,
)
{
    fun getCanonicalValues(exchangeId: UUID): ExchangeFieldSnapshot
    {
        val assignment = assignmentRepository.findByResource(ResourceType.EXCHANGE.name, exchangeId)
            ?: return ExchangeFieldSnapshot(hasAssignedSchema = false, valuesByFieldDefinitionId = emptyMap())

        val byFieldDefinitionId = HashMap<UUID, CanonicalFieldValue>()
        for (value in fieldValueRepository.findByAssignment(assignment.id))
        {
            val contract = fieldContractRepository.findById(value.fieldContractId) ?: continue
            val codes = if (value.valueType == FieldValueType.SINGLE_SELECT || value.valueType == FieldValueType.MULTI_SELECT)
                selectionRepository.findByValue(value.id).map { it.optionCode }
            else emptyList()

            byFieldDefinitionId[contract.fieldDefinitionId] = CanonicalFieldValue(
                type = value.valueType,
                isEmpty = CanonicalValueCodec.isEmpty(value, codes),
                textValue = value.textValue,
                numberValue = value.numberValue,
                boolValue = value.boolValue,
                dateValue = value.dateValue,
                datetimeValue = value.datetimeValue,
                selectionCodes = codes,
            )
        }
        return ExchangeFieldSnapshot(hasAssignedSchema = true, valuesByFieldDefinitionId = byFieldDefinitionId)
    }
}

/**
 * Canonical field values for one Exchange. [hasAssignedSchema] is false when no schema is assigned;
 * a field with no stored value is simply absent from [valuesByFieldDefinitionId].
 */
data class ExchangeFieldSnapshot(
    val hasAssignedSchema: Boolean,
    val valuesByFieldDefinitionId: Map<UUID, CanonicalFieldValue>,
)
