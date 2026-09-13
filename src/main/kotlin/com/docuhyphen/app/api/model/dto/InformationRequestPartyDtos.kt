package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/**
 * One request-scoped party as shown to one caller. [principalId], [principalKind],
 * [subjectIdentityRefId], and [exchangeRecipientId] are only populated for the caller's own party
 * row or for a caller holding party-management capability; every other caller sees a party's role
 * and status without learning who holds it.
 */
@Serializable
data class InformationRequestPartyDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val active: Boolean,
    @Serializable(with = UUIDSerializer::class) val principalId: UUID? = null,
    val principalKind: PrincipalKind? = null,
    @Serializable(with = UUIDSerializer::class) val subjectIdentityRefId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val exchangeRecipientId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val assignedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val revokedAt: Timestamp? = null,
    val partyRevision: Long,
    val partyETag: String,
)
