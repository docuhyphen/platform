package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "subscription_feature_entitlement")
class SubscriptionFeatureEntitlement
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: String

    @Column(name = "organization_id", nullable = true)
    var organizationId: UUID? = null

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
