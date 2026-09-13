package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * A platform-administered decision layered on top of the plan a subscription owner holds: an
 * enabled row adds a product feature the plan omits and a disabled row withdraws one it grants.
 *
 * The owner kind names exactly one of the two owner columns, so an organization's decision and a
 * person's decision never mix even when both ids carry the same value. The kind is stored as a
 * string alongside the subscription policy records, which do the same with plan and status codes.
 */
@Entity
@Table(name = "subscription_feature_entitlement")
class SubscriptionFeatureEntitlement
{
    @Id
    var id: UUID = UUID.randomUUID()

    /** Which kind of owner made this decision; the codes are the subscription owner types. */
    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: String

    /** Set when [ownerType] is `ORGANIZATION`; the organization the decision applies to. */
    @Column(name = "organization_id", nullable = true)
    var organizationId: UUID? = null

    /** Set when [ownerType] is `USER`; the individual account the decision applies to. */
    @Column(name = "app_user_id", nullable = true)
    var appUserId: UUID? = null

    @Column(name = "feature_code", nullable = false, length = 64)
    lateinit var featureCode: String

    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true

    @Column(name = "updated_by_app_user_id", nullable = false)
    lateinit var updatedByAppUserId: UUID

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())
}
