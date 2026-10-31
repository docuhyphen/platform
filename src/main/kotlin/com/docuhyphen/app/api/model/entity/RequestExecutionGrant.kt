package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestEntitlementSource
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

    @Column(name = "acting_party_cap")
    var actingPartyCap: Long? = null

    @Column(name = "evidence_file_allowance")
    var evidenceFileAllowance: Long? = null

    @Column(name = "evidence_byte_allowance")
    var evidenceByteAllowance: Long? = null

    @Column(name = "policy_version", nullable = false, length = 32)
    lateinit var policyVersion: String

    @Column(name = "entitlement_source", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var entitlementSource: InformationRequestEntitlementSource

    @Column(name = "trial_grant_id")
    var trialGrantId: UUID? = null

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "granted_features", nullable = false, columnDefinition = "varchar(64)[]")
    var grantedFeatures: Array<String> = emptyArray()

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "continuation_actions", nullable = false, columnDefinition = "varchar(64)[]")
    var continuationActions: Array<String> = emptyArray()

    @Column(name = "revoked_at")
    var revokedAt: Timestamp? = null

    @Column(name = "revoked_reason", length = 1024)
    var revokedReason: String? = null

    @Column(name = "issued_at", nullable = false)
    lateinit var issuedAt: Timestamp

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
