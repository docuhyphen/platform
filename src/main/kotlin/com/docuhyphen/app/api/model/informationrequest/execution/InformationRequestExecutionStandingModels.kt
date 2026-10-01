package com.docuhyphen.app.api.model.informationrequest.execution

import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import java.util.*

enum class InformationRequestExecutionStandingKind
{
    ACTIVE,
    NEW_WORK_UNAVAILABLE,
    CONTINUING_AFTER_LAPSE,
    OPERATIONALLY_SUSPENDED,
    EXECUTION_GRANT_REVOKED,
}

enum class InformationRequestStandingReason
{
    FEATURE_NOT_INCLUDED,
    TRIAL_ENDED,
    SUBSCRIPTION_PAST_DUE,
    SUBSCRIPTION_CANCELED,
    SUBSCRIPTION_SUSPENDED,
    EXECUTION_GRANT_REVOKED,
}

data class InformationRequestExecutionStanding(
    val kind: InformationRequestExecutionStandingKind,
    val reason: InformationRequestStandingReason? = null,
)
{
    fun forParticipant(): InformationRequestExecutionStanding = when (kind)
    {
        InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
        InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED -> copy(reason = null)

        else -> InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)
    }
}

data class InformationRequestOwnerStanding(
    val ownerType: SubscriptionOwnerType,
    val ownerId: UUID,
    val planCode: PlanCode,
    val status: SubscriptionStatus,
    val enforcementMode: SubscriptionEnforcementMode,
    val featureIncluded: Boolean,
    val operationallySuspended: Boolean,
    val newWorkUnavailableReason: InformationRequestStandingReason?,
)
{
    val newWorkAvailable: Boolean get() = newWorkUnavailableReason == null
}
