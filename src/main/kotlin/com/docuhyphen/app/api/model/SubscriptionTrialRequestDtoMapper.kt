package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.CurrentSubscriptionTrialRequestDto
import com.docuhyphen.app.api.model.dto.PlatformSubscriptionTrialRequestListDto
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestDto
import com.docuhyphen.app.api.service.subscription.CurrentSubscriptionTrialRequest
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestPage
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestView
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SubscriptionTrialRequestDtoMapper
{
    fun toDto(view: SubscriptionTrialRequestView): SubscriptionTrialRequestDto
    {
        val request = view.request
        return SubscriptionTrialRequestDto(
            id = request.id.toString(),
            ownerType = request.ownerType,
            ownerId = request.ownerId.toString(),
            ownerName = view.ownerName,
            requestedByAppUserId = request.requestedByAppUserId.toString(),
            requesterName = view.requesterName,
            requesterEmail = view.requesterEmail,
            planCode = request.planCode,
            status = request.status,
            requestNote = request.requestNote,
            requestedAt = request.requestedAt.toInstant().toString(),
            reviewedByAppUserId = request.reviewedByAppUserId?.toString(),
            reviewedAt = request.reviewedAt?.toInstant()?.toString(),
            decisionReason = request.decisionReason,
            trialGrantId = request.trialGrantId?.toString(),
        )
    }

    fun toDto(current: CurrentSubscriptionTrialRequest): CurrentSubscriptionTrialRequestDto =
        CurrentSubscriptionTrialRequestDto(
            eligible = current.eligibility.eligible,
            ineligibilityReason = current.eligibility.reason,
            request = current.request?.let(::toDto),
        )

    fun toDto(page: SubscriptionTrialRequestPage): PlatformSubscriptionTrialRequestListDto =
        PlatformSubscriptionTrialRequestListDto(
            total = page.total,
            limit = page.limit,
            offset = page.offset,
            items = page.items.map(::toDto),
        )
}
