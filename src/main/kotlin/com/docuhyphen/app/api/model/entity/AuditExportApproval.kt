package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * One dual-control approval of an [AuditExport] (Phase 6 of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`). Append-style: rows are only ever inserted, never
 * updated, so the identity and count of distinct approvers is directly queryable. [exportId] is a
 * denormalized reference (no FK), same rule as every other audit table.
 */
@Entity
@Table(name = "audit_export_approval")
class AuditExportApproval
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "export_id", nullable = false)
    var exportId: UUID = UUID.randomUUID()

    @Column(name = "approved_by_user_id", nullable = false)
    var approvedByUserId: UUID = UUID.randomUUID()

    @Column(name = "approved_at", nullable = false)
    var approvedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "note", length = 1024)
    var note: String? = null
}
