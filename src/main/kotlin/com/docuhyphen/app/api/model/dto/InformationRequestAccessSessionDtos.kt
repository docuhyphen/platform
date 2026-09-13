package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestAccessSessionDto(
    @Serializable(with = UUIDSerializer::class) val sessionId: UUID,
    val verificationStrength: RequestAccessSessionVerificationStrength,
    @Serializable(with = TimestampSerializer::class) val issuedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp,
    val sessionToken: String,
)
