package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

    @Column(name = "credential_hash", nullable = false, length = 64)
    lateinit var credentialHash: String

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

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: Timestamp

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Timestamp? = null

    @Column(name = "use_count", nullable = false)
    var useCount: Long = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "step_up_code_hash")
    var stepUpCodeHash: String? = null

    @Column(name = "step_up_code_expires_at")
    var stepUpCodeExpiresAt: Timestamp? = null

    @Column(name = "step_up_challenge_count", nullable = false)
    var stepUpChallengeCount: Int = 0

    @Column(name = "step_up_failed_attempts", nullable = false)
    var stepUpFailedAttempts: Int = 0

    @Column(name = "step_up_locked_until")
    var stepUpLockedUntil: Timestamp? = null

    @Column(name = "stepped_up_at")
    var steppedUpAt: Timestamp? = null

    constructor()
}
