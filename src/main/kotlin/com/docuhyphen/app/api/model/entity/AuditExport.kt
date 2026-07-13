package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * One requested, built, and downloaded verifiable evidence export. A normal mutable lifecycle row - like
 * [AuditEngagement], never the WORM evidence itself. The evidence is the signed bundle object
 * ([bundleObjectKey]) archived via `AuditArchiveStorage`, reusing the same archive
 * bucket or local directory as the WORM archive.
 *
 * Denormalized IDs only, no FK to any mutable business entity - same rule as every other audit
 * table.
 */
@Entity
@Table(name = "audit_export")
class AuditExport
{
    @Id
    var id: UUID = UUID.randomUUID()

    /** Null means a platform-scope export (no single owning organization). */
    @Column(name = "organization_id")
    var organizationId: UUID? = null

    @Column(name = "requested_by_user_id", nullable = false)
    var requestedByUserId: UUID = UUID.randomUUID()

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "categories_csv", nullable = false, columnDefinition = "text")
    var categoriesCsv: String = ""

    @Column(name = "occurred_after", nullable = false)
    var occurredAfter: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "occurred_before", nullable = false)
    var occurredBefore: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "purpose", nullable = false, length = 2048)
    var purpose: String = ""

    @Column(name = "case_reference", length = 256)
    var caseReference: String? = null

    @Column(name = "legal_basis", length = 2048)
    var legalBasis: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: AuditExportStatus = AuditExportStatus.REQUESTED

    @Column(name = "required_approvals", nullable = false)
    var requiredApprovals: Int = 0

    @Column(name = "approval_count", nullable = false)
    var approvalCount: Int = 0

    @Column(name = "built_at")
    var builtAt: Timestamp? = null

    @Column(name = "ready_at")
    var readyAt: Timestamp? = null

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "failed_at")
    var failedAt: Timestamp? = null

    @Column(name = "failure_reason", length = 2048)
    var failureReason: String? = null

    @Column(name = "revoked_by_user_id")
    var revokedByUserId: UUID? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "download_count", nullable = false)
    var downloadCount: Int = 0

    @Column(name = "download_limit")
    var downloadLimit: Int? = null

    @Column(name = "event_count")
    var eventCount: Int? = null

    @Column(name = "bundle_object_key", length = 512)
    var bundleObjectKey: String? = null

    @Column(name = "bundle_digest", length = 128)
    var bundleDigest: String? = null

    @Column(name = "signing_key_id", length = 64)
    var signingKeyId: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    /** Identifies the application node currently holding the build-claim lease. Meaningful only while [status] is `BUILDING`. */
    @Column(name = "build_worker_id", length = 128)
    var buildWorkerId: String? = null

    /** Expiry of the current build-claim lease. A null or past value means the export is unclaimed and eligible to be claimed. */
    @Column(name = "build_lease_expires_at")
    var buildLeaseExpiresAt: Timestamp? = null

    /** JPA optimistic-lock version: bumped on every update, so a transaction holding a stale read fails to commit over a newer state. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0
}
