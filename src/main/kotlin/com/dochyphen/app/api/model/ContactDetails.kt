package com.dochyphen.app.api.model

import com.dochyphen.app.api.serializer.UUIDSerializer
import com.dochyphen.app.api.serializer.TimestampSerializer
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
@Table(name = "contact_details")
@Serializable
class ContactDetails
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date")
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "phone_number")
    lateinit var phoneNumber: String

    @Column(name = "email")
    lateinit var email: String

    @OneToOne
    var company: Company? = null

    @OneToOne
    var person: Person? = null

    constructor()
}
