package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "authenticator_enrollment")
class AuthenticatorEnrollment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    lateinit var appUser: AppUser

    @Column(name = "provider", nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var provider: MultifactorAuthenticationType

    @Column(name = "secret_encrypted", nullable = false, length = 512)
    lateinit var secretEncrypted: String

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: Timestamp
}
