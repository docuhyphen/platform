package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

    @Column(name = "attempt_count")
    var attemptCount: Int = 0

    @Column(name = "session_id")
    var sessionId: String? = null

    @Column(name = "ip_address")
    var ipAddress: String? = null

    constructor()
}
