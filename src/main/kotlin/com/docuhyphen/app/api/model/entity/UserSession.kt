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
@Table(name = "user_session")
class UserSession
{
    @Id
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "app_user_id", nullable = false)
    var appUser: AppUser? = null

    @Column(name = "organization_id")
    var organizationId: UUID? = null

    @Column(name = "device_id")
    var deviceId: String? = null

    @Column(name = "device_name")
    var deviceName: String? = null

    @Column(name = "ip_address")
    var ipAddress: String? = null

    @Column(name = "user_agent", length = 1024)
    var userAgent: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "last_auth_time", nullable = false)
    var lastAuthTime: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "revocation_reason_code")
    var revocationReasonCode: String? = null

    @Column(name = "revoked_by_user_id")
    var revokedByUserId: UUID? = null

    @Column(name = "risk_flags", length = 1024)
    var riskFlags: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}

