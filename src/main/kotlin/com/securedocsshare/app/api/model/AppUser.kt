package com.securedocsshare.app.api.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.securedocsshare.app.api.model.MultifactorAuthenticationType.EMAIL
import com.securedocsshare.app.api.hacks.UUIDSerializer
import com.securedocsshare.app.api.hacks.TimestampSerializer
import jakarta.json.bind.annotation.JsonbTransient
import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class AppUserRole {
    USER,
    ADMIN
}

@Entity
@Serializable
@Table(name = "app_user")
class AppUser {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "email", nullable = false)
    lateinit var email: String

    @Column(name = "password", nullable = true)
    @JsonIgnore
    var password: String? = null

    @Column(name = "password_salt", nullable = true)
    @JsonIgnore
    var passwordSalt: String? = null

    @Column(name = "email_verification_completed", nullable = false)
    var emailVerificationComplete: Boolean = false

    @Column(name = "sign_in_attempts", nullable = false)
    @JsonIgnore
    var signInAttempts: Int = 0

    @Column(name = "multifactor_authentication_type", nullable = false)
    @Enumerated(STRING)
    var mfaType: MultifactorAuthenticationType = EMAIL

    @OneToOne(cascade = [ALL], fetch = LAZY)
    @JoinColumn(name = "person_id")
    var person: Person? = null

    @Enumerated(STRING)
    @Column(name = "role", nullable = false)
    var role: AppUserRole = AppUserRole.USER

    @Column(name = "is_temporary", nullable = false)
    var isTemporary: Boolean = false

    constructor()
}

