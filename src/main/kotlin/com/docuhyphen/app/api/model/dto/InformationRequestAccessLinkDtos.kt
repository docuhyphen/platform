package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/**
 * Returned only from issuance, rotation, and replacement, the three operations that mint a brand
 * new secret. [accessToken] is the one and only time that secret is ever readable; it is not
 * stored and cannot be recovered afterward.
 */
@Serializable
data class InformationRequestAccessLinkIssuedDto(
    @Serializable(with = UUIDSerializer::class) val shareLinkId: UUID,
    val accessToken: String,
    val status: ShareLinkStatus,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
    val rotationCount: Int,
)

/** The non-secret projection of a bootstrap access link, returned from revocation. */
@Serializable
data class InformationRequestAccessLinkDto(
    @Serializable(with = UUIDSerializer::class) val shareLinkId: UUID,
    val status: ShareLinkStatus,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
    val rotationCount: Int,
)
