package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * First-class machine identity, distinct from the existing [Application] row used for
 * the application/integration/service OAuth client. A [ServiceAccount] can hold
 * [RoleAssignment]s and be the principal on a [Share], so integrations/automations are
 * authorised through the same `AuthorizationService` codepath as humans.
 *
 * Authentication is delegated to the existing application-token machinery (the bearer
 * token presented at the edge resolves to a service account principal in iteration 2).
 */
@Entity
@Serializable
@Table(name = "service_account")
class ServiceAccount
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    /** Comma-separated scope tokens, mirroring [Application.apiKey] scope semantics. */
    @Column(name = "scopes", nullable = true, length = 2048)
    var scopes: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_by", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdBy: UUID? = null

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

