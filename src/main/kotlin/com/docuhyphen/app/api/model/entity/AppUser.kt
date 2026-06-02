package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.EMAIL
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.fasterxml.jackson.annotation.JsonIgnore
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
class AppUser
{
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

    @Column(name = "pending_email", nullable = true)
    var pendingEmail: String? = null

    @Column(name = "pending_email_verification_code", nullable = true)
    var pendingEmailVerificationCode: String? = null

    @Column(name = "pending_email_old_verification_code", nullable = true)
    var pendingEmailOldVerificationCode: String? = null

    @Column(name = "pending_email_old_verified", nullable = true)
    var pendingEmailOldVerified: Boolean? = false

    @Column(name = "email_verification_completed", nullable = true)
    var emailVerificationComplete: Boolean? = false

    @Column(name = "sign_in_attempts", nullable = false)
    @JsonIgnore
    var signInAttempts: Int = 0

    @Column(name = "multifactor_authentication_type", nullable = false)
    @Enumerated(STRING)
    var mfaType: MultifactorAuthenticationType = EMAIL

    @OneToOne(cascade = [ALL], fetch = LAZY)
    @JoinColumn(name = "person_id")
    var person: Person? = null

    @OneToOne(cascade = [ALL], fetch = LAZY)
    @JoinColumn(name = "application_id")
    var application: Application? = null

    // Roles now live in organization_membership.role_name (org scope) and role_assignment
    // (app scope) — resolved via UserRoleService. The legacy role columns are dropped at cutover.

    @Column(name = "is_temporary", nullable = false)
    var isTemporary: Boolean = false

    @Column(name = "is_password_temporary", nullable = false)
    var isPasswordTemporary: Boolean = false

    @Column(name = "temporary_password_expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var temporaryPasswordExpiresAt: Timestamp? = null

    @Column(name = "session_version", nullable = false)
    var sessionVersion: Long = 0

    @Column(name = "deprovisioned_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var deprovisionedAt: Timestamp? = null


    @OneToOne(cascade = [ALL], fetch = LAZY)
    @JoinColumn(name = "settings_id")
    var settings: AppUserSettings? = null

    @OneToMany(mappedBy = "appUser", cascade = [ALL], fetch = LAZY)
    @kotlinx.serialization.Transient
    var identityProviderLinks: MutableList<IdentityProviderLink> = mutableListOf()

    constructor()
}

