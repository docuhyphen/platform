package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestNoticeAttemptOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeChannel
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeEndpointState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeKind
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSourceKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestNoticeAttemptDto(
    val attemptNumber: Int,
    val outcome: InformationRequestNoticeAttemptOutcome,
    val failureCode: String? = null,
    @Serializable(with = TimestampSerializer::class) val attemptedAt: Timestamp,
)

@Serializable
data class InformationRequestNoticeSequenceAllocationDto(
    val sequenceKey: String,
    val renderedValue: String,
)

@Serializable
data class InformationRequestNoticeHistoryDto(
    @Serializable(with = UUIDSerializer::class) val noticeIntentId: UUID,
    val noticeKind: InformationRequestNoticeKind,
    @Serializable(with = UUIDSerializer::class) val partyId: UUID,
    val deliveryState: InformationRequestNoticeDeliveryState,
    @Serializable(with = TimestampSerializer::class) val owedAt: Timestamp,
    @Serializable(with = UUIDSerializer::class) val noticeId: UUID? = null,
    val channel: InformationRequestNoticeChannel? = null,
    val maskedEndpoint: String? = null,
    val endpointState: InformationRequestNoticeEndpointState? = null,
    val renderedSubject: String? = null,
    val renderedBody: String? = null,
    val renderedContentHash: String? = null,
    val sourceKind: InformationRequestNoticeSourceKind? = null,
    @Serializable(with = UUIDSerializer::class) val sourceCommunicationId: UUID? = null,
    val sourceContentHash: String? = null,
    @Serializable(with = TimestampSerializer::class) val renderedAt: Timestamp? = null,
    val attempts: List<InformationRequestNoticeAttemptDto> = emptyList(),
    val sequenceAllocations: List<InformationRequestNoticeSequenceAllocationDto> = emptyList(),
)
