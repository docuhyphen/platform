package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organization_subscription_policy")
class OrganizationSubscriptionPolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization? = null

    @Column(name = "tier_code", nullable = false)
    var tierCode: String = "FREE"

    @Column(name = "max_users")
    var maxUsers: Long? = null

    @Column(name = "change_reason", length = 1024)
    var changeReason: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())
}

