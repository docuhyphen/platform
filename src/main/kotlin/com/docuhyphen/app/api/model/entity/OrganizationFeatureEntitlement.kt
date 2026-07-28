package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "organization_feature_entitlement",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_organization_feature_entitlement_code",
            columnNames = ["organization_id", "feature_code"],
        ),
    ],
)
class OrganizationFeatureEntitlement
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id", nullable = false)
    lateinit var organizationId: UUID

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
