package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestLineageDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val lineageKind: InformationRequestLineageKind,
    @Serializable(with = UUIDSerializer::class) val sourceRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val successorRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourcePackageId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val recurrenceId: UUID? = null,
    val recurrenceSequence: Int? = null,
    @Serializable(with = UUIDSerializer::class) val refreshRuleId: UUID? = null,
    val reasonCode: String? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class InformationRequestLineageViewDto(
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    val source: InformationRequestLineageDto? = null,
    val successors: List<InformationRequestLineageDto> = emptyList(),
)

@Serializable
data class InformationRequestCarryForwardDto(
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID,
    val decision: InformationRequestCarryForwardDecision,
    val reasonCode: String? = null,
    @Serializable(with = UUIDSerializer::class) val sourcePackageId: UUID,
    val priorDisposition: InformationRequestResponseDisposition? = null,
    val priorNarrative: String? = null,
    val priorFieldValue: JsonElement? = null,
)

@Serializable
data class InformationRequestSuccessorResultDto(
    @Serializable(with = UUIDSerializer::class) val sourceRequestId: UUID,
    val sourceState: InformationRequestState,
    val successor: InformationRequestDto,
    val successorETag: String,
    val lineage: InformationRequestLineageDto,
    val offeredCount: Int,
    val invalidatedCount: Int,
)

@Serializable
data class InformationRequestRecurrenceDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val originRequestId: UUID,
    val intervalUnit: InformationRequestRecurrenceUnit,
    val intervalCount: Int,
    @Serializable(with = TimestampSerializer::class) val firstDueAt: Timestamp,
    val maximumOccurrences: Int? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class InformationRequestRefreshRuleDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    val requirementKey: String,
    val leadDays: Int,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)
