package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class OrganizationMembershipStatus
{
    INVITED,
    ACTIVE,
    SUSPENDED,
    LEFT,
}

/**
 * A user's membership of an organization. Organization roles are additive and stored in
 * `organization_membership_role`; changing one role does not replace unrelated roles.
 */
@Entity
@Serializable
@Table(name = "organization_membership")
class OrganizationMembership
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var appUserId: UUID

    @Column(name = "organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var organizationId: UUID

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "organization_membership_role",
        joinColumns = [JoinColumn(name = "organization_membership_id")],
    )
    @Column(name = "role_name", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    var roles: MutableSet<OrganizationRoleName> = mutableSetOf()

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var status: OrganizationMembershipStatus = OrganizationMembershipStatus.ACTIVE

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false

    @Column(name = "joined_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var joinedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "invited_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var invitedByAppUserId: UUID? = null

    @Column(name = "expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var expiresAt: Timestamp? = null

    @Column(name = "deprovisioned_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var deprovisionedAt: Timestamp? = null

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

