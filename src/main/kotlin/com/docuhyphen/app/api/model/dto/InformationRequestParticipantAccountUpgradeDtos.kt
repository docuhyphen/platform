package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestParticipantAccountUpgradeDto(
    @Serializable(with = UUIDSerializer::class) val participantAccountLinkId: UUID,
    @Serializable(with = UUIDSerializer::class) val participantId: UUID,
    @Serializable(with = UUIDSerializer::class) val appUserId: UUID,
    @Serializable(with = TimestampSerializer::class) val linkedAt: Timestamp,
    @Serializable(with = UUIDSerializer::class) val grantedShareId: UUID,
    val grantedRoleName: String,
    val respondentKey: String,
)
