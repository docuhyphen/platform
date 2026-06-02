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

    @Column(name = "allow_share_without_pairing", nullable = false)
    var allowShareWithoutPairing: Boolean = false

    /**
     * Whether this org may share with external **individual** customers — recipients who belong to
     * no organization (the headline B2C topology). Defaults to `true`: sharing to a person is not
     * federating into a managed tenant, so it is allowed out of the box. The B2B-unpaired case
     * (recipient belongs to another, non-paired org) stays gated by [allowShareWithoutPairing].
     */
    @Column(name = "allow_external_customer_sharing", nullable = false)
    var allowExternalCustomerSharing: Boolean = true

    @Column(name = "allow_profile_update", nullable = false)
    var allowProfileUpdate: Boolean = false

    @Column(name = "allow_email_update", nullable = false)
    var allowEmailUpdate: Boolean = false

    @OneToOne(mappedBy = "settings")
    @JsonIgnore
    var organization: Organization? = null

    constructor()
}