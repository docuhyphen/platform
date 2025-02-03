package com.securedocsshare.app.api.model

import com.securedocsshare.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mfa_record")
class MfaRecord {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "app_user_id")
    var appUser: AppUser? = null

    @Column(name = "created_date")
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expiry_date")
    var expiryDateTime: Timestamp? = null

    @Column(name = "mfa_token")
    var mfaToken: String? = null

    @Column(name = "mfa_type")
    @Enumerated(STRING)
    var mfaType: MultifactorAuthenticationType? = null

    @Column(name = "status")
    @Enumerated(STRING)
    var status: MultifactorAuthenticationStatus? = null

    constructor()
}
