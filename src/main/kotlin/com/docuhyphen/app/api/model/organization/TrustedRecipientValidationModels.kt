package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import java.time.Instant

data class TrustedExchangePolicyValidation(
    val relationship: OrganizationTrustRelationship,
    val callerOrganization: Organization,
    val targetOrganization: Organization,
    val senderPolicy: OrganizationTrustPartyPolicy,
    val targetPolicy: OrganizationTrustPartyPolicy,
    val verificationExpiresAt: Instant,
)

data class TrustedGroupValidation(
    val relationship: OrganizationTrustRelationship,
    val callerOrganization: Organization,
    val targetOrganization: Organization,
    val senderPolicy: OrganizationTrustPartyPolicy,
    val targetPolicy: OrganizationTrustPartyPolicy,
    val verificationExpiresAt: Instant,
    val group: PrincipalGroup,
)

data class TrustedPersonAcceptanceValidation(
    val relationship: OrganizationTrustRelationship,
    val membership: OrganizationMembership,
)
