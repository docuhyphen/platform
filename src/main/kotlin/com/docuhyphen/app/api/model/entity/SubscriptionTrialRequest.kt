package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscription_trial_request")
class SubscriptionTrialRequest
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: String

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Column(name = "requested_by_app_user_id", nullable = false)
    lateinit var requestedByAppUserId: UUID

    @Column(name = "plan_code", nullable = false, length = 32)
    lateinit var planCode: String

    @Column(name = "status", nullable = false, length = 32)
    var status: String = SubscriptionTrialRequestStatus.PENDING.name

    @Column(name = "request_note", length = 1024)
    var requestNote: String? = null

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "reviewed_by_app_user_id")
    var reviewedByAppUserId: UUID? = null

    @Column(name = "reviewed_at")
    var reviewedAt: Timestamp? = null

    @Column(name = "decision_reason", length = 1024)
    var decisionReason: String? = null

    @Column(name = "trial_grant_id")
    var trialGrantId: UUID? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}

enum class SubscriptionTrialRequestStatus
{
    PENDING,
    APPROVED,
    REJECTED,
}
