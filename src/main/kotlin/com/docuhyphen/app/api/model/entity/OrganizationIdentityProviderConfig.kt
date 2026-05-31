package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organization_identity_provider_config")
class OrganizationIdentityProviderConfig
{
    @Id
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    var organization: Organization? = null

    @Column(name = "provider", nullable = false)
    var provider: String = ""

    @Column(name = "client_id")
    var clientId: String? = null

    @Column(name = "client_secret_ref")
    var clientSecretRef: String? = null

    @Column(name = "tenant_id")
    var tenantId: String? = null

    @Column(name = "scopes", length = 1024)
    var scopes: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "access_token_expiry_minutes")
    var accessTokenExpiryMinutes: Long? = null

    @Column(name = "refresh_token_expiry_minutes")
    var refreshTokenExpiryMinutes: Long? = null

    @Column(name = "max_session_duration_hours")
    var maxSessionDurationHours: Long? = null

    @Column(name = "idle_timeout_minutes")
    var idleTimeoutMinutes: Long? = null

    @Column(name = "oidc_issuer")
    var oidcIssuer: String? = null

    @Column(name = "allowed_audiences", length = 2048)
    var allowedAudiences: String? = null

    @Column(name = "allowed_algs", length = 512)
    var allowedAlgs: String? = null

    @Column(name = "required_claims", length = 2048)
    var requiredClaims: String? = null

    @Column(name = "created_date", nullable = false)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "created_by")
    var createdBy: UUID? = null

    @Column(name = "updated_by")
    var updatedBy: UUID? = null
}

