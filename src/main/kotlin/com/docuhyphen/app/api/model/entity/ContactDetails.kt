package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
    var phoneNumber: String? = null

    @Column(name = "pending_phone_number")
    var pendingPhoneNumber: String? = null

    @Column(name = "phone_verification_code")
    var phoneVerificationCode: String? = null

    @Column(name = "is_phone_verified")
    var isPhoneVerified: Boolean? = false

    @Column(name = "email")
    var email: String? = null

    @Column(name = "email_verification_code")
    var emailVerificationCode: String? = null

    @Column(name = "pending_email")
    var pendingEmail: String? = null

    @Column(name = "is_email_verified")
    var isEmailVerified: Boolean? = false

    @OneToOne
    var organization: Organization? = null

    @OneToOne
    var person: Person? = null

    constructor()
}