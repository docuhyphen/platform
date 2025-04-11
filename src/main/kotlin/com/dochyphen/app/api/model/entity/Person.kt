package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.FetchType.EAGER
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "person")
@Serializable
class Person
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date")
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "first_name", nullable = false)
    var firstName: String? = null

    @Column(name = "last_name", nullable = false)
    var lastName: String? = null

    @Column(name = "identification_number")
    var identificationNumber: String? = null

    @Column(name = "person_id_type")
    @Enumerated(STRING)
    var personIDType: PersonIDType? = null

    @JoinColumn(name = "contact_details_id")
    @OneToOne(cascade = [(ALL)], fetch = EAGER)
    var contactDetails: ContactDetails? = null

//    var jobTitle: String? = null
//    var department: String? = null

    constructor()
}
