package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class ExternalIdentityResolutionDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID,
    val organizationName: String,
    val displayName: String?,
    val email: String,
    @Serializable(with = TimestampSerializer::class)
    val verifiedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val expiresAt: Timestamp,
)
