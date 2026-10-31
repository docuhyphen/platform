package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val exchangeId: UUID,
    @Serializable(with = UUIDSerializer::class) val templateVersionId: UUID? = null,
    val ownerType: InformationRequestOwnerType,
    @Serializable(with = UUIDSerializer::class) val ownerOrganizationId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val ownerUserId: UUID? = null,
    val state: InformationRequestState,
    val gatesExchangeClosure: Boolean,
    val aggregateRevision: Long,
    @Serializable(with = TimestampSerializer::class) val issuedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val closedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val cancelledAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val supersededAt: Timestamp? = null,
    @Serializable(with = UUIDSerializer::class) val supersededByRequestId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
    val requestETag: String,
    val executionStanding: InformationRequestExecutionStandingDto,
    val conditionEvaluations: List<InformationRequestConditionEvaluationDto> = emptyList(),
)

@Serializable
data class InformationRequestConditionEvaluationDto(
    val ruleKey: String,
    val expressionVersion: Int,
    val state: InformationRequestConditionEvaluationState,
    val occurrencePath: String,
)

@Serializable
data class InformationRequestGroupOccurrenceDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceTemplateGroupId: UUID,
    @Serializable(with = UUIDSerializer::class) val parentOccurrenceId: UUID? = null,
    val occurrenceIndex: Int,
    val occurrencePath: String,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val removedAt: Timestamp? = null,
)
