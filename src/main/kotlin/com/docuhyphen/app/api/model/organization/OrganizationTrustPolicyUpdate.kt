package com.docuhyphen.app.api.model.organization

import java.time.Instant

data class OrganizationTrustPolicyUpdate(
    val allowExchangesToPartner: Boolean,
    val allowExchangesFromPartner: Boolean,
    val allowPartnerMemberResolution: Boolean,
    val allowPartnerGroupDiscovery: Boolean,
    val shareMemberDisplayName: Boolean,
    val expiresAt: Instant?,
    val reviewDueAt: Instant?,
)
