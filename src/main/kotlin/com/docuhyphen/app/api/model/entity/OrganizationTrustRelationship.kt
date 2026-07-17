package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

enum class OrganizationTrustRelationshipStatus
{
    PENDING,
    ACTIVE,
    REJECTED,
    WITHDRAWN,
    EXPIRED,
    ENDED,
}

enum class OrganizationTrustDecision
{
    ACCEPT,
    REJECT,
}

@Entity
@Serializable
@Table(name = "organization_trust_relationship")
class OrganizationTrustRelationship
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_a_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var organizationAId: UUID

    @Column(name = "organization_b_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var organizationBId: UUID

    @Column(name = "requested_by_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var requestedByOrganizationId: UUID

    @Column(name = "requested_by_app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var requestedByAppUserId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OrganizationTrustRelationshipStatus = OrganizationTrustRelationshipStatus.PENDING

    @Column(name = "request_message", length = 2000)
    var requestMessage: String? = null

    @Column(name = "requested_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var requestedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "request_expires_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var requestExpiresAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "activated_at")
    @Serializable(with = TimestampSerializer::class)
    var activatedAt: Timestamp? = null

    @Column(name = "rejected_at")
    @Serializable(with = TimestampSerializer::class)
    var rejectedAt: Timestamp? = null

    @Column(name = "withdrawn_at")
    @Serializable(with = TimestampSerializer::class)
    var withdrawnAt: Timestamp? = null

    @Column(name = "expired_at")
    @Serializable(with = TimestampSerializer::class)
    var expiredAt: Timestamp? = null

    @Column(name = "ended_at")
    @Serializable(with = TimestampSerializer::class)
    var endedAt: Timestamp? = null

    @Column(name = "review_due_at")
    @Serializable(with = TimestampSerializer::class)
    var reviewDueAt: Timestamp? = null

    @Column(name = "latest_transition_by_app_user_id")
    @Serializable(with = UUIDSerializer::class)
    var latestTransitionByAppUserId: UUID? = null

    @Column(name = "latest_transition_reason", length = 2000)
    var latestTransitionReason: String? = null

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    fun includes(organizationId: UUID): Boolean =
        organizationAId == organizationId || organizationBId == organizationId

    fun partnerOf(organizationId: UUID): UUID = when (organizationId)
    {
        organizationAId -> organizationBId
        organizationBId -> organizationAId
        else -> throw IllegalArgumentException("Organization is not a party to the trust relationship")
    }
}
