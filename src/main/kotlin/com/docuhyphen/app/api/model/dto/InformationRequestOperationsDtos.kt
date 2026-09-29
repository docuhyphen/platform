package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStatus
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestOperationsRowDto(
    @Serializable(with = UUIDSerializer::class) val requestId: UUID,
    @Serializable(with = UUIDSerializer::class) val exchangeId: UUID,
    val title: String,
    val assignees: List<InformationRequestOperationsAssigneeDto>,
    @Serializable(with = UUIDSerializer::class) val templateVersionId: UUID,
    val state: InformationRequestState,
    val gatesExchangeClosure: Boolean,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val issuedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val firstViewedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val startedAt: Timestamp? = null,
    val ageSeconds: Long,
    val slaStatus: InformationRequestSlaStatus,
    @Serializable(with = TimestampSerializer::class) val nearestDueAt: Timestamp? = null,
    val clockCount: Int,
    val reminderCount: Int,
    val noticeCounts: Map<InformationRequestNoticeDeliveryState, Int>,
    val exceptionCounts: Map<InformationRequestOperationsException, Int>,
)

@Serializable
data class InformationRequestOperationsAssigneeDto(
    val roleKey: InformationRequestShareRoleKey,
    val principalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val principalId: UUID,
    val label: String? = null,
)

@Serializable
data class InformationRequestOperationsPageDto(
    val items: List<InformationRequestOperationsRowDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)
