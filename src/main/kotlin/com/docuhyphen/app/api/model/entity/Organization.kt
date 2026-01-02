package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "organization")
@Serializable
class Organization
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "verification_complete", nullable = false)
    var verificationComplete: Boolean = false

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "registration_number", nullable = false)
    lateinit var registrationNumber: String

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_details_id")
    var contactDetails: ContactDetails? = null

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    var appUsers: MutableList<AppUser> = mutableListOf()

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    var groups: MutableList<OrganizationGroup> = mutableListOf()

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "settings_id")
    var settings: OrganizationSettings? = null
    constructor()
}
