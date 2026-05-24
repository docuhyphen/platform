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
@Table(name = "admin_approval_request")
class AdminApprovalRequest
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "action", nullable = false)
    var action: String = ""

    @Column(name = "requester_id", nullable = false)
    var requesterId: UUID = UUID.randomUUID()

    @Column(name = "approver_id")
    var approverId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AdminApprovalStatus = AdminApprovalStatus.PENDING

    @Column(name = "reason", length = 1024)
    var reason: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "approved_date")
    var approvedDate: Timestamp? = null

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Timestamp = Timestamp.from(Instant.now().plusSeconds(3600))
}

