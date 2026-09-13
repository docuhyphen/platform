package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The frozen commercial position an Information Request was issued under.
 *
 * Captured once, at issuance, so a later change to the owner's plan (a paid lapse, a trial expiry,
 * a tightened limit) cannot strand a respondent or reviewer partway through work that was already
 * granted. Nothing in this row is ever recomputed from the live subscription after it is written.
 */
@Entity
@Table(name = "request_execution_grant")
class RequestExecutionGrant
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "request_id", nullable = false, unique = true)
    lateinit var requestId: UUID

    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: String

    @Column(name = "owner_organization_id")
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_user_id")
    var ownerUserId: UUID? = null

    @Column(name = "plan_code", nullable = false, length = 32)
    lateinit var planCode: String

    @Column(name = "subscription_status", nullable = false, length = 32)
    lateinit var subscriptionStatus: String

    @Column(name = "enforcement_mode", nullable = false, length = 16)
    lateinit var enforcementMode: String

    @Column(name = "trial_expires_at")
    var trialExpiresAt: Timestamp? = null

    @Column(name = "mutation_allowance_expires_at")
    var mutationAllowanceExpiresAt: Timestamp? = null

    @Column(name = "additional_recipient_cap")
    var additionalRecipientCap: Long? = null

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "revoked_reason", length = 1024)
    var revokedReason: String? = null

    @Column(name = "issued_at", nullable = false)
    lateinit var issuedAt: Timestamp

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
