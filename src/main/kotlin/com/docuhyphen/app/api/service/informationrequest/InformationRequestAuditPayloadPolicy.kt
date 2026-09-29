package com.docuhyphen.app.api.service.informationrequest

object InformationRequestAuditPayloadPolicy
{
    private val ALLOWED_KEYS = setOf(
        "acceptedFactId", "amendmentId", "amendmentNumber", "assignmentChange", "assignmentId", "attemptNumber",
        "basis", "budgetMinutes", "businessDecisionId", "businessTimezone", "changeCount", "claimId", "clockId",
        "clockKey", "clockType", "commentId", "commentRole", "commentVisibility", "conflictState", "connectorExchangeId",
        "connectorKey", "contentHash",
        "correctionId", "correctionScope", "decisionCount", "decisionKind", "decisionRevision", "deletedObjectCount",
        "deletionOutcome", "discrepancyCount", "discrepancyId", "discrepancyResolution", "documentVersionId", "dueAt",
        "dueEffect", "endpointState", "engine", "evidenceAction",
        "evidenceArtifactId", "evidenceVersionNumber", "exchangeId", "expiredAt", "exportId", "extensionMinutes",
        "factConfidence", "failureCode", "findingId", "findingSeverity", "findingVisibility", "fromState",
        "fromTemplateVersionId", "gatesExchangeClosure", "generatedOutputId", "holdCount", "importedValueDecision",
        "importedValueId", "lineageKind", "mutation", "namespace",
        "noticeId", "noticeIntentCount", "noticeIntentId", "noticeKind", "objectCount", "orgId", "outcome",
        "outcomeCode", "outputKey", "override", "owningProcessKey", "packageNumber", "partyId", "pausedAt", "pendingNoticeCount",
        "pointAt", "policyKey", "policyVersionId", "previousDueAt", "priorDecisionId", "priorReviewId",
        "privacyRequestId", "processedAt", "productionEligible", "purposeKey", "reasonCode", "receivedAt",
        "recurrenceId", "refreshRuleId", "reminderOrdinal", "remainingSeconds", "renderedContentHash", "requestId",
        "requestKind", "requirementId", "resultKey", "resumedAt", "retainedObjectCount", "returnedRequirementCount", "reused",
        "reviewId", "reviewKind", "reviewOutcome", "satisfiedByPackageId", "scanOutcome", "schemaVersion", "scopeKind",
        "sequenceAllocationCount", "sequenceNumber", "signatureVersion", "sourceKind", "sourcePackageId", "stageKey",
        "state", "stoppedAt", "storageLocation", "submissionItemId", "submissionPackageId", "submissionPackageNumber",
        "subjectIdentityRefId", "successorRequestId", "supersededByRequestId", "templateKey", "templateVersionId",
        "toState", "toTemplateVersionId", "transferDecision", "transitionId", "transitionSequence", "urgency",
        "versionNumber", "worksheetEntryCount",
    )

    fun allowedPayload(payload: Map<String, String>): Map<String, String> = payload.filterKeys { it in ALLOWED_KEYS }

    fun withheldKeyCount(payload: Map<String, String>): Int = payload.keys.count { it !in ALLOWED_KEYS }

    fun eventClassOf(eventTypeKey: String): String
    {
        val segments = eventTypeKey.split('.')
        return when
        {
            segments.size >= 2 && segments.first() == "information_request" -> segments[1]
            segments.size >= 2 && segments.first() == "record" -> "record_${segments[1]}"
            else -> segments.first()
        }
    }
}
