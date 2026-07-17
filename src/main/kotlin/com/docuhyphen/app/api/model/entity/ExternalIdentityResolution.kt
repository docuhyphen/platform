package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Entity
@Serializable
@Table(name = "external_identity_resolution")
class ExternalIdentityResolution
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "actor_app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var actorAppUserId: UUID

    @Column(name = "caller_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var callerOrganizationId: UUID

    @Column(name = "target_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var targetOrganizationId: UUID

    @Column(name = "relationship_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var relationshipId: UUID

    @Column(name = "sender_policy_revision", nullable = false)
    var senderPolicyRevision: Long = 0

    @Column(name = "target_policy_revision", nullable = false)
    var targetPolicyRevision: Long = 0

    @Column(name = "resolved_app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var resolvedAppUserId: UUID

    @Column(name = "resolved_membership_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var resolvedMembershipId: UUID

    @Column(name = "normalized_email", nullable = false, length = 320)
    lateinit var normalizedEmail: String

    @Column(name = "display_name_snapshot", length = 512)
    var displayNameSnapshot: String? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    lateinit var createdAt: Timestamp

    @Column(name = "expires_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    lateinit var expiresAt: Timestamp

    @Column(name = "consumed_at")
    @Serializable(with = TimestampSerializer::class)
    var consumedAt: Timestamp? = null

    @Column(name = "consumed_by_exchange_id")
    @Serializable(with = UUIDSerializer::class)
    var consumedByExchangeId: UUID? = null
}
