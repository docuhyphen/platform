package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class IssueInformationRequestAccessLinkRequest(
    @Serializable(with = UUIDSerializer::class) val partyId: UUID,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
)

@Serializable
data class ReplaceInformationRequestAccessLinkRequest(
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
)

@Serializable
data class VerifyInformationRequestContactProofRequest(
    val otp: String,
)

@Serializable
data class UpgradeInformationRequestParticipantAccountRequest(
    @Serializable(with = UUIDSerializer::class) val sessionId: UUID,
)
