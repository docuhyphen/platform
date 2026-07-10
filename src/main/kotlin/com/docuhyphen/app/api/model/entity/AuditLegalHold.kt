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
 * A time-unbounded hold on a denormalized resource reference that overrides any retention-driven
 * disposal for that resource until explicitly released. Never references the resource via a
 * foreign key, same rule as every other audit table.
 */
@Entity
@Table(name = "audit_legal_hold")
class AuditLegalHold
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    @Column(name = "resource_type", nullable = false, length = 64)
    lateinit var resourceType: String

    @Column(name = "resource_id", nullable = false, length = 128)
    lateinit var resourceId: String

    @Column(name = "reason", nullable = false, length = 2048)
    var reason: String = ""

    @Column(name = "case_reference", length = 256)
    var caseReference: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: AuditLegalHoldStatus = AuditLegalHoldStatus.ACTIVE

    @Column(name = "placed_by_user_id", nullable = false)
    var placedByUserId: UUID = UUID.randomUUID()

    @Column(name = "placed_at", nullable = false)
    var placedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "released_by_user_id")
    var releasedByUserId: UUID? = null

    @Column(name = "released_at")
    var releasedAt: Timestamp? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())
}
