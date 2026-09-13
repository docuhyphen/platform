package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.fields.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * Read-only projection of an Exchange's stored field values into a comparable canonical form keyed
 * by the stable [com.docuhyphen.app.api.model.entity.FieldDefinition] id. Consumers (e.g. the
 * workflow applicability evaluator) call this method instead of touching the Fields repositories,
 * per the backend service-to-service rule. Stored [FieldValue] rows are keyed by `fieldContractId`;
 * this resolves each to its stable `fieldDefinitionId` via the [FieldContract] and surfaces
 * selection option codes for SINGLE_SELECT / MULTI_SELECT.
 *
 * A condition asks about the Exchange itself, so only the root Value Set is read. An answer stored
 * for a repetition of a group belongs to that repetition and never stands in for the Exchange's own.
 */
@ApplicationScoped
class ExchangeFieldQueryService @Inject constructor(
    private val assignmentRepository: SchemaAssignmentRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val fieldValueSetRepository: FieldValueSetRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val selectionRepository: FieldValueSelectionRepository,
)
{
    fun getCanonicalValues(exchangeId: UUID): ExchangeFieldSnapshot
    {
        val assignment = assignmentRepository.findByResource(ResourceType.EXCHANGE.name, exchangeId)
            ?: return ExchangeFieldSnapshot(hasAssignedSchema = false, valuesByFieldDefinitionId = emptyMap())

        val rootValueSet = fieldValueSetRepository.findRoot(assignment.id)
            ?: return ExchangeFieldSnapshot(hasAssignedSchema = true, valuesByFieldDefinitionId = emptyMap())

        val byFieldDefinitionId = HashMap<UUID, CanonicalFieldValue>()
        for (value in fieldValueRepository.findByValueSet(rootValueSet.id))
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
                datetimeValue = value.datetimeValue?.toInstant(),
                datetimeOffsetMinutes = value.datetimeOffsetMinutes,
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
