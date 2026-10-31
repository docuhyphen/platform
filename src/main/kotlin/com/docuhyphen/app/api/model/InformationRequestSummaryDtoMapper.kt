package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestAssignedWork
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestExchangeListing
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestSummary
import java.sql.Timestamp

object InformationRequestSummaryDtoMapper
{
    fun toDto(listing: InformationRequestExchangeListing): InformationRequestExchangeListingDto =
        InformationRequestExchangeListingDto(
            requests = listing.requests.map(::toDto),
            canCreate = listing.canCreate,
            creationUnavailableReason = listing.creationUnavailableReason,
            creationOwner = listing.creationOwner?.let(::toDto),
        )

    fun toDto(work: InformationRequestAssignedWork): InformationRequestAssignedWorkDto =
        InformationRequestAssignedWorkDto(
            request = toDto(work.summary),
            exchangeName = work.exchangeName,
        )

    private fun toDto(owner: InformationRequestOwnerRef): InformationRequestOwnerDto =
        InformationRequestOwnerDto(
            ownerType = owner.ownerType,
            ownerOrganizationId = owner.organizationId,
            ownerUserId = owner.userId,
        )

    fun toDto(summary: InformationRequestSummary): InformationRequestSummaryDto =
        InformationRequestSummaryDto(
            id = summary.request.id,
            exchangeId = summary.request.exchangeId,
            title = summary.title,
            state = summary.request.state,
            issuedAt = summary.request.issuedAt,
            nextDueAt = summary.nextDueAt?.let(Timestamp::from),
            completedCount = summary.completedCount,
            requiredCount = summary.requiredCount,
            callerRoles = summary.standing.roles,
            permissions = InformationRequestSummaryPermissionsDto(
                canManage = summary.standing.permissions.canManage,
                canRespond = summary.standing.permissions.canRespond,
                canReview = summary.standing.permissions.canReview,
            ),
            nextAction = summary.standing.nextAction,
            executionStanding = InformationRequestExecutionStandingDtoMapper.toDto(summary.executionStanding),
        )
}
