package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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

    @Column(name = "allow_profile_update", nullable = false)
    var allowProfileUpdate: Boolean = false

    @Column(name = "allow_email_update", nullable = false)
    var allowEmailUpdate: Boolean = false

    @OneToOne(mappedBy = "settings")
    @JsonIgnore
    var organization: Organization? = null

    constructor()
}