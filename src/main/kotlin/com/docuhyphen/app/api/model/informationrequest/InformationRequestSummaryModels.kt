package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import java.time.Instant

enum class InformationRequestNextAction
{
    COMPLETE_SETUP,
    RESPOND,
    REVIEW,
    MANAGE,
    VIEW,
}

data class InformationRequestSummaryPermissions(
    val canManage: Boolean,
    val canRespond: Boolean,
    val canReview: Boolean,
)

data class InformationRequestCallerStanding(
    val roles: List<InformationRequestShareRoleKey>,
    val permissions: InformationRequestSummaryPermissions,
    val nextAction: InformationRequestNextAction,
)

data class InformationRequestSummary(
    val request: InformationRequest,
    val title: String,
    val nextDueAt: Instant?,
    val completedCount: Int,
    val requiredCount: Int,
    val standing: InformationRequestCallerStanding,
)

data class InformationRequestExchangeListing(
    val requests: List<InformationRequestSummary>,
    val canCreate: Boolean,
)
