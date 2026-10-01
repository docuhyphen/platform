package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import java.util.UUID

object InformationRequestActiveResponseProjection
{
    fun isActive(ruleKey: String?, occurrencePath: String,
                 evaluations: List<InformationRequestConditionEvaluationProjection>, activeEnvelope: Boolean = true): Boolean
    {
        if (!activeEnvelope) return false
        if (ruleKey == null) return true
        val evaluation = evaluations.firstOrNull { it.ruleKey == ruleKey && it.occurrencePath == occurrencePath }
            ?: evaluations.firstOrNull { it.ruleKey == ruleKey && InformationRequestOccurrencePath.isRoot(it.occurrencePath) }
        return evaluation?.state == InformationRequestConditionEvaluationState.TRUE
    }

    fun field(projection: SchemaAssignmentDto, fieldDefinitionId: UUID?): SchemaAssignmentDto
    {
        val bindings = projection.bindings.filter { it.fieldDefinitionId == fieldDefinitionId }
        val contracts = bindings.map { it.fieldContractId }.toSet()
        return projection.copy(bindings = bindings, fields = projection.fields.filter { it.fieldContractId in contracts })
    }

    fun schema(projections: Collection<SchemaAssignmentDto?>): SchemaAssignmentDto? =
        projections.filterNotNull().firstOrNull()?.copy(
            bindings = projections.filterNotNull().flatMap { it.bindings }.distinctBy { it.id },
            fields = emptyList(),
        )
}
