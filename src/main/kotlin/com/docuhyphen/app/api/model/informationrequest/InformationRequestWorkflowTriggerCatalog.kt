package com.docuhyphen.app.api.model.informationrequest

object InformationRequestWorkflowTriggerCatalog
{
    const val SUBJECT_RESOURCE_TYPE = "INFORMATION_REQUEST"
    const val SUBJECT_SCHEMA_VERSION = 1

    private val COMMON = listOf("requestId", "exchangeId", "templateVersionId", "orgId", "state", "transitionSequence")

    private val EVENT_FIELDS: Map<String, List<String>> = mapOf(
        "information_request.request.issue" to COMMON,
        "information_request.request.view" to COMMON,
        "information_request.request.start" to COMMON,
        "information_request.request.submit" to COMMON + listOf("submissionPackageId", "packageNumber", "stageKey"),
        "information_request.request.correction" to COMMON + listOf(
            "submissionPackageId", "reviewId", "correctionId", "returnedRequirementCount", "stageKey",
        ),
        "information_request.request.close" to COMMON + listOf("submissionPackageId"),
        "information_request.request.expire" to COMMON + listOf("reasonCode", "clockId", "clockKey"),
        "information_request.request.cancel" to COMMON + listOf("reasonCode"),
        "information_request.request.supersede" to COMMON + listOf("supersededByRequestId", "reasonCode"),
        "information_request.request.overdue" to COMMON + listOf("clockId", "clockKey", "dueAt"),
    )

    val eventNames: Set<String> = EVENT_FIELDS.keys

    val packageScopedEventNames: Set<String> = setOf(
        "information_request.request.submit",
        "information_request.request.correction",
        "information_request.request.close",
    )

    fun fieldsOf(eventName: String): List<String> = EVENT_FIELDS[eventName].orEmpty()
}
