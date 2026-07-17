package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.OrganizationTrustDecision
import kotlinx.serialization.Serializable

@Serializable
data class OrganizationDirectorySearchRequest(val query: String? = null)

@Serializable
data class OrganizationTrustRelationshipCreateRequest(
    val targetOrganizationId: String? = null,
    val requestMessage: String? = null,
)

@Serializable
data class OrganizationTrustDecisionRequest(
    val decision: OrganizationTrustDecision,
    val reason: String? = null,
    val expectedVersion: Long,
)

@Serializable
data class OrganizationTrustWithdrawalRequest(
    val reason: String? = null,
    val expectedVersion: Long,
)

@Serializable
data class OrganizationTrustSuspensionRequest(val reason: String? = null)

@Serializable
data class OrganizationTrustTerminationRequest(
    val reason: String? = null,
    val expectedVersion: Long,
)

@Serializable
data class OrganizationTrustPolicyUpdateRequest(
    val expectedRevision: Long,
    val allowExchangesToPartner: Boolean,
    val allowExchangesFromPartner: Boolean,
    val allowPartnerMemberResolution: Boolean,
    val allowPartnerGroupDiscovery: Boolean,
    val shareMemberDisplayName: Boolean,
    val expiresAt: Long? = null,
    val reviewDueAt: Long? = null,
)
