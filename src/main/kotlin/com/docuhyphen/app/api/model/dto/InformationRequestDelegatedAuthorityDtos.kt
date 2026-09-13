package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestDelegatedAuthorityDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val informationRequestId: UUID,
    @Serializable(with = UUIDSerializer::class) val assignedPartyId: UUID,
    val delegatePrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val delegatePrincipalId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID? = null,
    val active: Boolean,
    val grantorPrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val grantorPrincipalId: UUID,
    val authorityInstrumentRef: String? = null,
    @Serializable(with = TimestampSerializer::class) val effectiveAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val revokedAt: Timestamp? = null,
    val revokedByPrincipalKind: PrincipalKind? = null,
    @Serializable(with = UUIDSerializer::class) val revokedByPrincipalId: UUID? = null,
    val revocationReason: String? = null,
    @Serializable(with = TimestampSerializer::class) val recordedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
)
