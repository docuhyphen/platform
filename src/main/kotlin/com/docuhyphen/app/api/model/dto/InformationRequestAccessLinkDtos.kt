package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestAccessLinkIssuedDto(
    @Serializable(with = UUIDSerializer::class) val shareLinkId: UUID,
    val accessToken: String,
    val status: ShareLinkStatus,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
    val rotationCount: Int,
    val partyETag: String? = null,
)

@Serializable
data class InformationRequestAccessLinkDto(
    @Serializable(with = UUIDSerializer::class) val shareLinkId: UUID,
    val status: ShareLinkStatus,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
    val rotationCount: Int,
    @Serializable(with = UUIDSerializer::class) val partyId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp? = null,
    val partyETag: String? = null,
)
