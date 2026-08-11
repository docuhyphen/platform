package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The subscription held by a registered organization. Business is the only organization plan,
 * and `maxUsers` is the purchased seat capacity that active provisioned memberships consume.
 * A null `maxUsers` means seat capacity has not been assigned yet and the organization is
 * uncapped until a platform administrator sets it.
 */
@Entity
@Table(
    name = "organization_subscription_policy",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_organization_subscription_policy_organization",
            columnNames = ["organization_id"],
        ),
    ],
)
class OrganizationSubscriptionPolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization? = null

    @Column(name = "tier_code", nullable = false)
    var tierCode: String = "BUSINESS"

    @Column(name = "max_users")
    var maxUsers: Long? = null

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

