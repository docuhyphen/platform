package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscription_trial_grant")
class SubscriptionTrialGrant
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: String

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Column(name = "plan_code", nullable = false, length = 32)
    lateinit var planCode: String

    @Column(name = "started_at", nullable = false)
    lateinit var startedAt: Timestamp

    @Column(name = "ended_at", nullable = false)
    lateinit var endedAt: Timestamp

    @Column(name = "source", nullable = false, length = 32)
    lateinit var source: String

    @Column(name = "granted_by_app_user_id")
    var grantedByAppUserId: UUID? = null

    @Column(name = "reason", nullable = false, length = 1024)
    lateinit var reason: String

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
