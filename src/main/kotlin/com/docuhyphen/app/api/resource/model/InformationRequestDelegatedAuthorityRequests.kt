package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class GrantInformationRequestDelegatedAuthorityRequest(
    @Serializable(with = UUIDSerializer::class) val assignedPartyId: UUID,
    val delegatePrincipalKind: PrincipalKind,
    @Serializable(with = UUIDSerializer::class) val delegatePrincipalId: UUID,
    @Serializable(with = UUIDSerializer::class) val requirementId: UUID? = null,
    val authorityInstrumentRef: String? = null,
    @Serializable(with = TimestampSerializer::class) val effectiveAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val expiresAt: Timestamp? = null,
)

@Serializable
data class RevokeInformationRequestDelegatedAuthorityRequest(
    val reason: String? = null,
)
