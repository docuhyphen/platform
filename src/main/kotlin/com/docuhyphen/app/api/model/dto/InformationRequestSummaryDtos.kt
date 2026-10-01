package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestNextAction
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestSummaryDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val exchangeId: UUID,
    val title: String,
    val state: InformationRequestState,
    @Serializable(with = TimestampSerializer::class) val issuedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val nextDueAt: Timestamp? = null,
    val completedCount: Int,
    val requiredCount: Int,
    val callerRoles: List<InformationRequestShareRoleKey>,
    val permissions: InformationRequestSummaryPermissionsDto,
    val nextAction: InformationRequestNextAction,
    val executionStanding: InformationRequestExecutionStandingDto,
)

@Serializable
data class InformationRequestSummaryPermissionsDto(
    val canManage: Boolean,
    val canRespond: Boolean,
    val canReview: Boolean,
)

@Serializable
data class InformationRequestExchangeListingDto(
    val requests: List<InformationRequestSummaryDto>,
    val canCreate: Boolean,
    val creationUnavailableReason: InformationRequestStandingReason? = null,
)
