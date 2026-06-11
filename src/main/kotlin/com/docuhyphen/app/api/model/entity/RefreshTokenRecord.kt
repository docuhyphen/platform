package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_token")
class RefreshTokenRecord
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID()

    @Column(name = "user_exchange_id")
    var userSessionId: UUID? = null

    @Column(name = "family_id", nullable = false)
    var familyId: String = ""

    @Column(name = "jti", nullable = false, unique = true)
    var jti: String = ""

    @Column(name = "token_hash", nullable = false, length = 128)
    var tokenHash: String = ""

    @Column(name = "rotated_from_jti")
    var rotatedFromJti: String? = null

    @Column(name = "status", nullable = false)
    var status: String = "ACTIVE"

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "grace_until")
    var graceUntil: Timestamp? = null

    @Column(name = "consumed_at")
    var consumedAt: Timestamp? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "revocation_reason_code")
    var revocationReasonCode: String? = null
}

