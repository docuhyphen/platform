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
    OPEN_REQUEST_LIMIT_REACHED,
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

data class InformationRequestPlanQuotas(
    val maxOpenRequests: Long?,
    val openRequests: Long,
    val maxActingPartiesPerRequest: Long?,
    val maxEvidenceFilesPerRequest: Long?,
    val maxEvidenceBytesPerRequest: Long?,
    val maxCommittedEvidenceBytes: Long?,
    val committedEvidenceBytes: Long,
)

data class InformationRequestCreationStanding(
    val owner: InformationRequestOwnerStanding,
    val quotas: InformationRequestPlanQuotas,
    val unavailableReason: InformationRequestStandingReason?,
) {
    val available: Boolean get() = unavailableReason == null
}
