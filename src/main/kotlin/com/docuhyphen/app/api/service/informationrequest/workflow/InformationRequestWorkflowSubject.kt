package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.model.informationrequest.workflow.InformationRequestWorkflowTriggerCatalog
import com.docuhyphen.app.api.service.notification.DomainEvent

object InformationRequestWorkflowSubject
{
    fun of(event: DomainEvent): Map<String, String>
    {
        val payload = event.payload
        val candidates = mapOf(
            "requestId" to event.subject?.id,
            "exchangeId" to payload["exchangeId"],
            "templateVersionId" to payload["templateVersionId"],
            "orgId" to event.organizationId,
            "state" to payload["toState"],
            "transitionSequence" to payload["sequenceNumber"],
            "submissionPackageId" to (payload["submissionPackageId"] ?: payload["satisfiedByPackageId"]),
            "packageNumber" to payload["submissionPackageNumber"],
            "stageKey" to payload["stageKey"],
            "reviewId" to payload["reviewId"],
            "correctionId" to payload["correctionId"],
            "returnedRequirementCount" to payload["returnedRequirementCount"],
            "reasonCode" to payload["reasonCode"],
            "supersededByRequestId" to payload["supersededByRequestId"],
            "clockId" to payload["clockId"],
            "clockKey" to payload["clockKey"],
            "dueAt" to payload["dueAt"],
        )
        return InformationRequestWorkflowTriggerCatalog.fieldsOf(event.type)
            .mapNotNull { field -> candidates[field]?.trim()?.takeIf { it.isNotBlank() }?.let { field to it } }
            .toMap()
    }
}
