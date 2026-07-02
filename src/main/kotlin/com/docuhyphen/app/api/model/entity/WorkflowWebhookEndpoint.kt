package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Webhook endpoint registered for outbound delivery by the workflow engine.
 *
 * Signing credentials are stored in [signingSecretToken] (raw Base64 secret) and are separate from the
 * inbound application API credentials in [Application]. Rotation replaces both
 * [signingSecretToken] and increments [signingSecretVersion]; a bounded overlap window
 * lets in-flight deliveries complete with the previous version before it is invalidated.
 *
 * [targetUrl] is validated against [WebhookDestinationPolicy] on create and update to
 * prevent SSRF via loopback, link-local, private-range, and metadata-service addresses.
 */
@Entity
@Table(name = "workflow_webhook_endpoint")
@Serializable
class WorkflowWebhookEndpoint
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Serializable(with = UUIDSerializer::class)
    @Column(name = "owner_organization_id", nullable = false)
    lateinit var ownerOrganizationId: UUID

    @Serializable(with = UUIDSerializer::class)
    @Column(name = "workflow_definition_id", nullable = false)
    lateinit var workflowDefinitionId: UUID

    @Serializable(with = UUIDSerializer::class)
    @Column(name = "registered_application_id")
    var registeredApplicationId: UUID? = null

    @Column(name = "target_url", nullable = false, length = 2048)
    lateinit var targetUrl: String

    @Column(name = "signing_secret_token", nullable = false, length = 256)
    lateinit var signingSecretToken: String

    @Column(name = "signing_secret_version", nullable = false)
    var signingSecretVersion: Int = 1

    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true

    @Column(name = "permitted_event_types", nullable = false, length = 1024)
    var permittedEventTypes: String = "[]"

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
