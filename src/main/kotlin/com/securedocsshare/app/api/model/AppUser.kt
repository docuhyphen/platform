package com.securedocsshare.app.api.model

import com.securedocsshare.app.api.model.MultifactorAuthenticationType.EMAIL
import com.securedocsshare.app.hacks.CustomerSerializers
import com.securedocsshare.app.hacks.TimestampSerializer
import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.LAZY
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "app_user")
class AppUser {

    @Id
    @Serializable(with = CustomerSerializers::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "email", nullable = false)
    lateinit var email: String

    @Column(name = "password", nullable = false)
    lateinit var password: String

    @Column(name = "password_salt", nullable = false)
    lateinit var passwordSalt: String

    @Column(name = "verification_completed", nullable = false)
    var verificationCompleted: Boolean = false

    @Column(name = "sign_in_attempts", nullable = false)
    var signInAttempts: Int = 0

    @Column(name = "multifactor_authentication_type", nullable = false)
    @Enumerated(STRING)
    var mfaType: MultifactorAuthenticationType = EMAIL

    @OneToOne(cascade = [ALL], fetch = LAZY)
    @JoinColumn(name = "person_id")
    var person: Person? = null

//    var roles: Array<String> = arrayOf()

    constructor(email: String, password: String)

    constructor()
}

