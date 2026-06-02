package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/**
 * One entry in a resource's unified access view (see v2 plan §6.2). Sourced from the `share`
 * table — replaces the legacy three-way recipient + per-session permission representation.
 */
@Serializable
data class SessionAccessEntryDto(
    @Serializable(with = UUIDSerializer::class)
    val shareId: UUID,
    val principalKind: String,
    @Serializable(with = UUIDSerializer::class)
    val principalId: UUID,
    /** Best-effort human label (user email / group name); null if it can't be resolved. */
    val displayName: String? = null,
    val roleName: String,
    val source: String,
    val status: String,
    @Serializable(with = UUIDSerializer::class)
    val grantedByAppUserId: UUID? = null,
    @Serializable(with = TimestampSerializer::class)
    val grantedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val expiresAt: Timestamp? = null,
    val constraintsJson: String? = null,
)
