package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.*

enum class SignUpStatus {
    PENDING,
    VERIFIED,
    EXPIRED,
    EXPIRED_MAX_RETRIES,
    OTP_LOCKED
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

//    @Column(name = "minutes_til_next_otp_attempt", nullable = false)
//    var minutesTilOtpNextAttempt: LocalDateTime = LocalDateTime.now()

    @Column(name = "otp_expiry_timestamp", nullable = false)
    lateinit var otpExpiryTimestamp: LocalDateTime

    @Column(name = "otp_attempts", nullable = false)
    var otpAttempts: Int = 0

    @Column(name = "status", nullable = false)
    @Enumerated(value = EnumType.STRING)
    var status: SignUpStatus = SignUpStatus.PENDING

    @Column(name = "otp_regeneration_attempts", nullable = false)
    var otpRegenerationAttempts: Int = 0

    @Column(name = "last_regeneration_attempt_time", nullable = true)
    var lastRegenerationAttemptTime: LocalDateTime? = null

    constructor()
}
