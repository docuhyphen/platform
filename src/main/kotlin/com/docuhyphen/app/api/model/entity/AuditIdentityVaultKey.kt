package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * A per-subject data key, wrapped (AES-GCM) with the application-wide master key from
 * [com.docuhyphen.app.api.service.audit.identity.AuditIdentityVaultMasterKeyProvider]. Nulling
 * [wrappedKey]/[iv] and stamping [shreddedAt] is the crypto-shredding action itself: anything ever
 * encrypted with this subject's data key becomes permanently unrecoverable, with no audit row
 * having to be deleted.
 */
@Entity
@Table(name = "audit_identity_vault_key")
class AuditIdentityVaultKey
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "subject_type", nullable = false, length = 32)
    lateinit var subjectType: String

    @Column(name = "subject_id", nullable = false, length = 128)
    lateinit var subjectId: String

    @Column(name = "wrapped_key", columnDefinition = "text")
    var wrappedKey: String? = null

    @Column(name = "iv", columnDefinition = "text")
    var iv: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "shredded_at")
    var shreddedAt: Timestamp? = null
}
