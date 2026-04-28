package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "auth_token")
class AuthToken {

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

    @Column(name = "token", nullable = false, length = 2048)
    var token: String? = null

    @Column(name = "jti", nullable = true, unique = true)
    var jti: String? = null

    @Column(name = "token_type", nullable = true)
    @Enumerated(EnumType.STRING)
    var tokenType: AuthTokenType? = AuthTokenType.REFRESH

    @Column(name = "otp")
    var otp: String? = null

    constructor()
}
