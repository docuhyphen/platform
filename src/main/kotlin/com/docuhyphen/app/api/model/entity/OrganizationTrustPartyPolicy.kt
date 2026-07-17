package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Serializable
@Table(name = "organization_trust_party_policy")
class OrganizationTrustPartyPolicy
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "relationship_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var relationshipId: UUID

    @Column(name = "policy_owner_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var policyOwnerOrganizationId: UUID

    @Column(name = "allow_exchanges_to_partner", nullable = false)
    var allowExchangesToPartner: Boolean = false

    @Column(name = "allow_exchanges_from_partner", nullable = false)
    var allowExchangesFromPartner: Boolean = false

    @Column(name = "allow_partner_member_resolution", nullable = false)
    var allowPartnerMemberResolution: Boolean = false

    @Column(name = "allow_partner_group_discovery", nullable = false)
    var allowPartnerGroupDiscovery: Boolean = false

    @Column(name = "share_member_display_name", nullable = false)
    var shareMemberDisplayName: Boolean = false

    @Column(name = "expires_at")
    @Serializable(with = TimestampSerializer::class)
    var expiresAt: Timestamp? = null

    @Column(name = "review_due_at")
    @Serializable(with = TimestampSerializer::class)
    var reviewDueAt: Timestamp? = null

    @Version
    @Column(name = "revision", nullable = false)
    var revision: Long = 0

    @Column(name = "updated_by_app_user_id")
    @Serializable(with = UUIDSerializer::class)
    var updatedByAppUserId: UUID? = null

    @Column(name = "updated_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}
