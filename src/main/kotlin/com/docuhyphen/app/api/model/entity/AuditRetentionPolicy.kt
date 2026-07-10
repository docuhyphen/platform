package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * An organization's override of the platform-default retention catalog for one
 * [com.docuhyphen.app.api.service.audit.catalog.AuditCategory]. There is no platform-default row
 * in this table - the default catalog lives in code
 * ([com.docuhyphen.app.api.service.audit.AuditRetentionCatalogService]), so every organization
 * always has a well-defined effective policy even with zero rows here.
 */
@Entity
@Table(name = "audit_retention_policy")
class AuditRetentionPolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID = UUID.randomUUID()

    @Column(name = "category", nullable = false, length = 32)
    lateinit var category: String

    @Column(name = "ledger_retention_days", nullable = false)
    var ledgerRetentionDays: Int = 0

    @Column(name = "archive_retention_days", nullable = false)
    var archiveRetentionDays: Int = 0

    @Column(name = "legal_hold_eligible", nullable = false)
    var legalHoldEligible: Boolean = true

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_treatment", nullable = false, length = 32)
    var identityTreatment: AuditIdentityTreatment = AuditIdentityTreatment.READABLE

    @Column(name = "updated_by_user_id", nullable = false)
    var updatedByUserId: UUID = UUID.randomUUID()

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}
