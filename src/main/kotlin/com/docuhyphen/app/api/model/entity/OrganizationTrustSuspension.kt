package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Serializable
@Table(name = "organization_trust_suspension")
class OrganizationTrustSuspension
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "relationship_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var relationshipId: UUID

    @Column(name = "suspending_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var suspendingOrganizationId: UUID

    @Column(name = "reason", nullable = false, length = 2000)
    lateinit var reason: String

    @Column(name = "suspended_by_app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var suspendedByAppUserId: UUID

    @Column(name = "suspended_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var suspendedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "cleared_by_app_user_id")
    @Serializable(with = UUIDSerializer::class)
    var clearedByAppUserId: UUID? = null

    @Column(name = "cleared_at")
    @Serializable(with = TimestampSerializer::class)
    var clearedAt: Timestamp? = null

    fun isActive(): Boolean = clearedAt == null
}
