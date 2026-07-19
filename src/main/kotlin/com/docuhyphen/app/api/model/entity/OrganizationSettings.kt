package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "organization_settings")
class OrganizationSettings
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())

    /**
     * When true (**default**), members of this organization may share an Exchange with a recipient
     * belonging to another organization only when both organizations, their trust relationship,
     * and both directional policies are current and eligible. When false, cross-organization
     * sharing is unrestricted.
     */
    @Column(name = "require_trusted_organization_for_b2b", nullable = false)
    var requireTrustedOrganizationForB2b: Boolean = true

    @Column(name = "discoverable_for_trust_requests", nullable = false)
    var discoverableForTrustRequests: Boolean = false

    /**
     * Whether this org may share with external **individual** customers, recipients who belong to
     * no organization (the headline B2C topology). Defaults to `true`: sharing to a person is not
     * federating into a managed tenant, so it is allowed out of the box. The B2B case (recipient
     * belongs to another organization) stays gated by [requireTrustedOrganizationForB2b].
     */
    @Column(name = "allow_external_customer_sharing", nullable = false)
    var allowExternalCustomerSharing: Boolean = true

    @Column(name = "allow_profile_update", nullable = false)
    var allowProfileUpdate: Boolean = false

    @Column(name = "allow_email_update", nullable = false)
    var allowEmailUpdate: Boolean = false

    /**
     * When true (default), an Exchange stays at INITIATED until at least one recipient
     * explicitly accepts it (via a workflow or a direct status update). When false, the
     * exchange auto-advances to ACCEPTED_STARTED immediately on creation.
     */
    @Column(name = "require_recipient_acceptance", nullable = false)
    var requireRecipientAcceptance: Boolean = true

    @OneToOne(mappedBy = "settings")
    @JsonIgnore
    var organization: Organization? = null

    constructor()
}
