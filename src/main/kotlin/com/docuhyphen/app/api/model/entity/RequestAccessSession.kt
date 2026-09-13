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

enum class RequestAccessSessionVerificationStrength
{
    EMAIL_OTP,
}

@Entity
@Table(name = "request_access_session")
class RequestAccessSession
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "credential_hash", length = 64)
    var credentialHash: String? = null

    @Column(name = "share_link_id", nullable = false)
    lateinit var shareLinkId: UUID

    @Column(name = "participant_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var participantPrincipalKind: PrincipalKind

    @Column(name = "participant_principal_id", nullable = false)
    lateinit var participantPrincipalId: UUID

    @Column(name = "verification_strength", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var verificationStrength: RequestAccessSessionVerificationStrength =
        RequestAccessSessionVerificationStrength.EMAIL_OTP

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Timestamp? = null

    @Column(name = "use_count", nullable = false)
    var useCount: Long = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
