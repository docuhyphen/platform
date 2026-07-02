package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Serializable
@Table(name = "app_role_assignment")
class AppRoleAssignment
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var appUserId: UUID

    @Column(name = "role_name", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    var roleName: AppRoleName = AppRoleName.APP_USER

    @Column(name = "granted_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var grantedByAppUserId: UUID? = null

    @Column(name = "granted_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var grantedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "expires_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var expiresAt: Timestamp? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    constructor()
}
