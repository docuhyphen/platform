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
 * Minimal authority relation used by runtime authorization facts.
 */
@Entity
@Table(name = "information_request_delegated_authority")
class InformationRequestDelegatedAuthority
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "assigned_party_id", nullable = false)
    lateinit var assignedPartyId: UUID

    @Column(name = "delegate_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var delegatePrincipalKind: PrincipalKind

    @Column(name = "delegate_principal_id", nullable = false)
    lateinit var delegatePrincipalId: UUID

    @Column(name = "requirement_id")
    var requirementId: UUID? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @Column(name = "grantor_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var grantorPrincipalKind: PrincipalKind

    @Column(name = "grantor_principal_id", nullable = false)
    lateinit var grantorPrincipalId: UUID

    @Column(name = "authority_instrument_ref", length = 512)
    var authorityInstrumentRef: String? = null

    @Column(name = "effective_at", nullable = false)
    var effectiveAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at")
    var expiresAt: Timestamp? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "revoked_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var revokedByPrincipalKind: PrincipalKind? = null

    @Column(name = "revoked_by_principal_id")
    var revokedByPrincipalId: UUID? = null

    @Column(name = "revocation_reason", length = 512)
    var revocationReason: String? = null

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
