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

/**
 * One request-scoped party. A subject party names what the request concerns; acting parties name a
 * principal that can later receive a request-scoped Share.
 */
@Entity
@Table(name = "information_request_party")
class InformationRequestParty
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "role_key", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var roleKey: InformationRequestShareRoleKey

    @Column(name = "subject_identity_ref_id")
    var subjectIdentityRefId: UUID? = null

    @Column(name = "principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var principalKind: PrincipalKind? = null

    @Column(name = "principal_id")
    var principalId: UUID? = null

    @Column(name = "exchange_recipient_id")
    var exchangeRecipientId: UUID? = null

    @Column(name = "share_id")
    var shareId: UUID? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @Column(name = "party_revision", nullable = false)
    var partyRevision: Long = 1

    @Column(name = "assigned_by_app_user_id")
    var assignedByAppUserId: UUID? = null

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
