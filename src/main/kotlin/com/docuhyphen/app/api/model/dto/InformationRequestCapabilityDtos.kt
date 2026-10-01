package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestCapabilitiesDto(
    val ownerType: SubscriptionOwnerType,
    val planCode: PlanCode,
    val subscriptionStatus: SubscriptionStatus,
    val enforcementMode: SubscriptionEnforcementMode,
    val featureIncluded: Boolean,
    val newWorkAvailable: Boolean,
    val newWorkUnavailableReason: InformationRequestStandingReason? = null,
    val operationallySuspended: Boolean,
    val typedAnswersAvailable: Boolean,
    val personalTemplatesAvailable: Boolean,
    val assignedWork: Boolean,
    val holdsRequests: Boolean,
)
