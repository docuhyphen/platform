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

@Entity
@Table(name = "audit_engagement")
class AuditEngagement
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    @Column(name = "resource_type", length = 64)
    var resourceType: String? = null

    @Column(name = "resource_id", length = 128)
    var resourceId: String? = null

    @Column(name = "auditor_user_id")
    var auditorUserId: UUID? = null

    @Column(name = "principal_group_id")
    var principalGroupId: UUID? = null

    @Column(name = "categories_csv", nullable = false, columnDefinition = "text")
    var categoriesCsv: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity_level", nullable = false, length = 32)
    var sensitivityLevel: AuditEngagementSensitivity = AuditEngagementSensitivity.METADATA_ONLY

    @Column(name = "starts_at", nullable = false)
    var startsAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "purpose", nullable = false, length = 2048)
    var purpose: String = ""

    @Column(name = "case_reference", length = 256)
    var caseReference: String? = null

    @Column(name = "legal_basis", nullable = false, length = 2048)
    var legalBasis: String = ""

    @Column(name = "export_permitted", nullable = false)
    var exportPermitted: Boolean = false

    @Column(name = "max_query_range_days")
    var maxQueryRangeDays: Int? = null

    @Column(name = "download_limit")
    var downloadLimit: Int? = null

    @Column(name = "requested_by_user_id", nullable = false)
    var requestedByUserId: UUID = UUID.randomUUID()

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "approved_by_user_id")
    var approvedByUserId: UUID? = null

    @Column(name = "approved_at")
    var approvedAt: Timestamp? = null

    @Column(name = "denied_by_user_id")
    var deniedByUserId: UUID? = null

    @Column(name = "denied_at")
    var deniedAt: Timestamp? = null

    @Column(name = "revoked_by_user_id")
    var revokedByUserId: UUID? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: AuditEngagementStatus = AuditEngagementStatus.REQUESTED

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}
