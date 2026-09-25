package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestAmendmentDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val amendmentNumber: Int,
    @Serializable(with = UUIDSerializer::class) val fromTemplateVersionId: UUID,
    @Serializable(with = UUIDSerializer::class) val toTemplateVersionId: UUID,
    val reasonCode: String? = null,
    @Serializable(with = TimestampSerializer::class) val amendedAt: Timestamp,
    val amendedByCaller: Boolean,
    val changes: List<InformationRequestAmendmentChangeDto> = emptyList(),
    val undisclosedChangeCount: Int = 0,
    val notices: List<InformationRequestNoticeIntentDto> = emptyList(),
)

@Serializable
data class InformationRequestAmendmentChangeDto(
    val requirementKey: String,
    val changeKind: InformationRequestAmendmentChangeKind,
    val reconfirmationRequired: Boolean,
)

@Serializable
data class InformationRequestNoticeIntentDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val partyId: UUID,
    val noticeKind: InformationRequestNoticeKind,
    val deliveryState: InformationRequestNoticeDeliveryState,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class InformationRequestAmendmentResultDto(
    val requestState: InformationRequestState,
    val requestETag: String,
    val amendment: InformationRequestAmendmentDto,
)
