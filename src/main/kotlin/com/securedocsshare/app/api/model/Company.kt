package com.securedocsshare.app.api.model

import com.securedocsshare.app.hacks.CustomerSerializers
import com.securedocsshare.app.hacks.TimestampSerializer
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType.LAZY
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "company")
@Serializable
class Company {

    @Id
    @Serializable(with = CustomerSerializers::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "registration_number", nullable = false)
    lateinit var registrationNumber: String

    @OneToOne(cascade = [ALL], fetch = LAZY)
    @JoinColumn(name = "contact_details_id")
    var contactDetails: ContactDetails? = null

    @OneToMany(cascade = [ALL], fetch = LAZY)
    var appUsers: MutableList<AppUser> = mutableListOf()

    constructor()
}
