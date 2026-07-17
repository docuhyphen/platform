package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class OrganizationDirectoryEntryDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
)

@Serializable
data class OrganizationTrustRelationshipDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val currentOrganizationId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val partnerOrganizationId: UUID,
    val partnerOrganizationName: String,
    val partnerOrganizationActive: Boolean,
    val partnerOrganizationVerified: Boolean,
    val requestedByCurrentOrganization: Boolean,
    val status: OrganizationTrustRelationshipStatus,
    val requestMessage: String?,
    @Serializable(with = TimestampSerializer::class)
    val requestedAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val requestExpiresAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val activatedAt: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val endedAt: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val reviewDueAt: Timestamp?,
    val version: Long,
    val effectivelySuspended: Boolean,
    val suspendedByCurrentOrganization: Boolean,
    val suspendedByPartner: Boolean,
    @Serializable(with = UUIDSerializer::class)
    val currentOrganizationSuspensionId: UUID?,
)

@Serializable
data class OrganizationTrustPolicyDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val relationshipId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val policyOwnerOrganizationId: UUID,
    val policyOwnerOrganizationName: String,
    val ownedByCurrentOrganization: Boolean,
    val allowExchangesToPartner: Boolean,
    val allowExchangesFromPartner: Boolean,
    val allowPartnerMemberResolution: Boolean,
    val allowPartnerGroupDiscovery: Boolean,
    val shareMemberDisplayName: Boolean,
    @Serializable(with = TimestampSerializer::class)
    val expiresAt: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val reviewDueAt: Timestamp?,
    val revision: Long,
    @Serializable(with = TimestampSerializer::class)
    val updatedAt: Timestamp,
)

@Serializable
data class OrganizationTrustPoliciesDto(
    @Serializable(with = UUIDSerializer::class)
    val relationshipId: UUID,
    val policies: List<OrganizationTrustPolicyDto>,
)
