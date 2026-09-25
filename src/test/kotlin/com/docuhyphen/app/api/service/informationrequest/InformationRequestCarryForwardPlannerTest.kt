package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorOccurrence
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestCarryForwardPlannerTest
{
    @Test
    fun `answers are offered while evidence, attestations, and unanswered items are invalidated by stable reason`()
    {
        val answered = occurrence("recorded-value", InformationRequestRequirementType.FIELD)
        val unanswered = occurrence("optional-value", InformationRequestRequirementType.FIELD)
        val document = occurrence("supporting-record", InformationRequestRequirementType.DOCUMENT)
        val assertion = occurrence("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION)
        val unmatched = occurrence("new-value", InformationRequestRequirementType.FIELD)
        val items = listOf(
            item(answered, InformationRequestResponseDisposition.PROVIDED, fieldValueRevision = UUID.randomUUID()),
            item(unanswered, InformationRequestResponseDisposition.NOT_ANSWERED),
            item(document, InformationRequestResponseDisposition.PROVIDED),
            item(assertion, InformationRequestResponseDisposition.PROVIDED),
            item(occurrence("recorded-value", InformationRequestRequirementType.FIELD, "entries[1]"), InformationRequestResponseDisposition.PROVIDED),
        )

        val planned = InformationRequestCarryForwardPlanner.plan(listOf(answered, unanswered, document, assertion, unmatched), items)
            .associate { it.requirementId to (it.decision to it.reasonCode) }

        assertEquals(
            mapOf(
                answered.requirementId to (InformationRequestCarryForwardDecision.OFFERED to null),
                unanswered.requirementId to
                    (InformationRequestCarryForwardDecision.INVALIDATED to InformationRequestCarryForwardPlanner.SOURCE_NOT_ANSWERED),
                document.requirementId to
                    (InformationRequestCarryForwardDecision.INVALIDATED to InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION),
                assertion.requirementId to
                    (InformationRequestCarryForwardDecision.INVALIDATED to InformationRequestCarryForwardPlanner.ATTESTATION_REQUIRES_FRESH_ASSENT),
            ),
            planned,
        )
    }

    private fun occurrence(key: String, type: InformationRequestRequirementType, path: String = "root") =
        InformationRequestSuccessorOccurrence(UUID.randomUUID(), key, type, path)

    private fun item(
        occurrence: InformationRequestSuccessorOccurrence,
        disposition: InformationRequestResponseDisposition,
        fieldValueRevision: UUID? = null,
    ) = InformationRequestSubmissionItem().apply {
        requirementKey = occurrence.requirementKey
        requirementType = occurrence.requirementType
        occurrencePath = occurrence.occurrencePath
        this.disposition = disposition
        completenessState = InformationRequestCompletenessItemState.COMPLETE
        fieldValueRevisionId = fieldValueRevision
    }
}
