package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.informationrequest.InformationRequestPlannedCarryForward
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorOccurrence
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState

object InformationRequestCarryForwardPlanner
{
    const val EVIDENCE_REQUIRES_FRESH_COLLECTION = "EVIDENCE_REQUIRES_FRESH_COLLECTION"
    const val ATTESTATION_REQUIRES_FRESH_ASSENT = "ATTESTATION_REQUIRES_FRESH_ASSENT"
    const val SOURCE_NOT_ANSWERED = "SOURCE_NOT_ANSWERED"

    fun plan(
        occurrences: List<InformationRequestSuccessorOccurrence>,
        sourceItems: List<InformationRequestSubmissionItem>,
    ): List<InformationRequestPlannedCarryForward>
    {
        val itemsByOccurrence = sourceItems.associateBy { Triple(it.requirementKey, it.requirementType, it.occurrencePath) }
        return occurrences.mapNotNull { occurrence ->
            val item = itemsByOccurrence[Triple(occurrence.requirementKey, occurrence.requirementType, occurrence.occurrencePath)]
                ?: return@mapNotNull null
            val reason = when
            {
                item.requirementType == InformationRequestRequirementType.DOCUMENT -> EVIDENCE_REQUIRES_FRESH_COLLECTION
                item.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION -> ATTESTATION_REQUIRES_FRESH_ASSENT
                item.completenessState == InformationRequestCompletenessItemState.HIDDEN ||
                    (item.disposition == InformationRequestResponseDisposition.NOT_ANSWERED && item.fieldValueRevisionId == null) ->
                    SOURCE_NOT_ANSWERED
                else -> null
            }
            InformationRequestPlannedCarryForward(
                requirementId = occurrence.requirementId,
                sourceItemId = item.id,
                decision = if (reason == null) InformationRequestCarryForwardDecision.OFFERED else InformationRequestCarryForwardDecision.INVALIDATED,
                reasonCode = reason,
            )
        }
    }
}
