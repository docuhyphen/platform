package com.securedocsshare.app.api.model

import com.securedocsshare.app.api.hacks.CustomerSerializers
import com.securedocsshare.app.api.hacks.TimestampSerializer
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.FetchType.LAZY
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "person")
@Serializable
class Person
{
    @Id
    @Serializable(with = CustomerSerializers::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date")
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "first_name")
    var firstName: String? = null

    @Column(name = "last_name")
    var lastName: String? = null

    @Column(name = "identification_number")
    var identificationNumber: String? = null

    @Column(name = "person_id_type")
    @Enumerated(STRING)
    var personIDType: PersonIDType? = null

    @JoinColumn(name = "contact_details_id")
    @OneToOne(cascade = [(ALL)], fetch = LAZY)
    var contactDetails: ContactDetails? = null

    constructor()
}
