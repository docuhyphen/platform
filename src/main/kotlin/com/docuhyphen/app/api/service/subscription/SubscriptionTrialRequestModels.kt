package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequest

data class SubscriptionTrialRequestView(
    val request: SubscriptionTrialRequest,
    val ownerName: String,
    val requesterName: String,
    val requesterEmail: String,
)

data class SubscriptionTrialRequestEligibility(
    val eligible: Boolean,
    val reason: String? = null,
)

data class CurrentSubscriptionTrialRequest(
    val eligibility: SubscriptionTrialRequestEligibility,
    val request: SubscriptionTrialRequestView?,
)

data class SubscriptionTrialRequestPage(
    val total: Long,
    val limit: Int,
    val offset: Int,
    val items: List<SubscriptionTrialRequestView>,
)

class SubscriptionTrialRequestConflictException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
