package com.docuhyphen.app.api.service.informationrequest.execution

import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.service.subscription.PlanFeature

internal val typedAnswerGrantFeatures: List<String> =
    listOf(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS, PlanFeature.INFORMATION_REQUESTS).map { it.name }

internal val everyContinuationAction: List<String> =
    InformationRequestContinuationAction.entries.map { it.name }.sorted()

internal fun issuedExecutionGrant(): RequestExecutionGrant = RequestExecutionGrant().apply {
    grantedFeatures = typedAnswerGrantFeatures.toTypedArray()
    continuationActions = everyContinuationAction.toTypedArray()
}
