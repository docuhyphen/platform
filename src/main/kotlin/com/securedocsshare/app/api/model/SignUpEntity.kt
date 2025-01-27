package com.securedocsshare.app.api.model

import com.securedocsshare.app.api.model.SignUpStatus.PENDING
import com.securedocsshare.app.api.hacks.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.UUID

enum class SignUpStatus {
    PENDING,
    VERIFIED,
    EXPIRED
}

@Entity
@Table(name = "sign_up")
class SignUpEntity
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(nullable = false, unique = true)
    lateinit var email: String

    @Column(nullable = false)
    lateinit var otp: String

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: LocalDateTime

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0

    @Column(name = "status", nullable = false)
    @Enumerated(value = EnumType.STRING)
    var status: SignUpStatus = PENDING

    constructor()
}
