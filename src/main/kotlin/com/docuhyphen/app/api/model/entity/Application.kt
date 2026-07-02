package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "application")
@Serializable
class Application
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "description")
    var description: String? = null

    @Column(name = "api_key", nullable = false)
    lateinit var apiKey: String

    @Column(name = "api_secret_hash", nullable = false)
    lateinit var apiSecretHash: String

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 32)
    var roleName: ApplicationRoleName = ApplicationRoleName.APPLICATION

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false)
    var applicationType: ApplicationType = ApplicationType.SERVICE

    @Column(name = "last_access_date")
    @Serializable(with = TimestampSerializer::class)
    var lastAccessDate: Timestamp? = null

    @Serializable(with = UUIDSerializer::class)
    @Column(name = "owner_organization_id")
    var ownerOrganizationId: UUID? = null

    // JSON array of Capability enum names explicitly granted to this application.
    // Resolved to Set<Capability> in DefaultAuthorizationService. Stored as a plain
    // JSON string rather than a relation because capability grants are a small, stable
    // set managed by app-admins, not a high-churn join table.
    @Column(name = "granted_capabilities", nullable = false)
    var grantedCapabilitiesJson: String = "[]"

    constructor()
}
