package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import java.util.*

object InformationRequestActiveFieldValues {
    fun withoutInactiveFields(
        valuesByOccurrencePath: Map<String, Map<UUID, CanonicalFieldValue?>>,
        requirements: Collection<InformationRequestRequirement>,
        inactiveRequirementIds: Set<UUID>,
        bindingsById: Map<UUID, InformationRequestTemplateRequirementBinding>,
    ): Map<String, Map<UUID, CanonicalFieldValue?>> {
        val collected = requirements.mapNotNull { requirement ->
            bindingsById[requirement.sourceTemplateBindingId]?.collectedFieldDefinitionId
                ?.let { Triple(requirement.occurrencePath, it, requirement.id in inactiveRequirementIds) }
        }
        val activelyCollected = collected.filterNot { it.third }.map { it.first to it.second }.toSet()
        val absent = collected
            .filter { it.third && (it.first to it.second) !in activelyCollected }
            .groupBy({ it.first }, { it.second })
        val unanswered = activelyCollected.groupBy({ it.first }, { it.second })
        if (absent.isEmpty() && unanswered.isEmpty()) return valuesByOccurrencePath
        return (valuesByOccurrencePath.keys + absent.keys + unanswered.keys).associateWith { occurrencePath ->
            val values = valuesByOccurrencePath[occurrencePath].orEmpty()
            val empty = unanswered[occurrencePath].orEmpty().filterNot { values.containsKey(it) }
            values + empty.associateWith { null } + absent[occurrencePath].orEmpty().associateWith { null }
        }
    }
}
