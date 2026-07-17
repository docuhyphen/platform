package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class ExchangeRecipientDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val exchangeId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val directShareId: UUID,
    val purpose: ExchangeRecipientPurpose,
    val selectionType: ExchangeRecipientSelectionType,
    @Serializable(with = UUIDSerializer::class)
    val targetOrganizationId: UUID? = null,
    val acceptanceStatus: ExchangeRecipientAcceptanceStatus,
    @Serializable(with = UUIDSerializer::class)
    val acceptedOrRejectedByAppUserId: UUID? = null,
    @Serializable(with = TimestampSerializer::class)
    val acceptedOrRejectedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
)
