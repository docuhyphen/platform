package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The subscription held by an individual registered account. Only the individual plans (Free
 * and Personal) are valid here; organization subscriptions live in
 * [OrganizationSubscriptionPolicy]. Temporary no-account recipients and service accounts never
 * get a row.
 */
@Entity
@Table(
    name = "user_subscription_policy",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_subscription_policy_app_user",
            columnNames = ["app_user_id"],
        ),
    ],
)
class UserSubscriptionPolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = false)
    lateinit var appUserId: UUID

    @Column(name = "plan_code", nullable = false, length = 32)
    var planCode: String = "FREE"

    @Column(name = "subscription_status", nullable = false, length = 32)
    var subscriptionStatus: String = "ACTIVE"

    @Column(name = "billing_frequency", length = 16)
    var billingFrequency: String? = null

    @Column(name = "current_period_start")
    var currentPeriodStart: Timestamp? = null

    @Column(name = "current_period_end")
    var currentPeriodEnd: Timestamp? = null

    @Column(name = "grace_period_end")
    var gracePeriodEnd: Timestamp? = null

    @Column(name = "external_billing_customer_ref", length = 255)
    var externalBillingCustomerRef: String? = null

    @Column(name = "external_billing_subscription_ref", length = 255)
    var externalBillingSubscriptionRef: String? = null

    @Column(name = "change_reason", length = 1024)
    var changeReason: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())
}

