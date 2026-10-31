package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.fields.ExchangeFieldSnapshot
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class ExchangeFieldQueryService @Inject constructor(
    private val assignmentRepository: SchemaAssignmentRepository,
    private val fieldValueSetRepository: FieldValueSetRepository,
    private val projectionLoader: FieldsProjectionLoader,
)
{
    fun getCanonicalValues(exchangeId: UUID): ExchangeFieldSnapshot
    {
        val assignment = assignmentRepository.findByResource(ResourceType.EXCHANGE.name, exchangeId)
            ?: return ExchangeFieldSnapshot(hasAssignedSchema = false, valuesByFieldDefinitionId = emptyMap())
        val rootValueSet = fieldValueSetRepository.findRoot(assignment.id)
            ?: throw IllegalStateException("Schema assignment ${assignment.id} has no root value set")

        val valuesByFieldDefinitionId = projectionLoader.resolveValues(assignment.schemaVersionId, rootValueSet)
            .mapNotNull { resolved ->
                resolved.stored?.let { stored ->
                    resolved.question.definition.id to CanonicalValueCodec.toCanonical(stored, resolved.selectionCodes)
                }
            }
            .toMap()
        return ExchangeFieldSnapshot(hasAssignedSchema = true, valuesByFieldDefinitionId = valuesByFieldDefinitionId)
    }
}
