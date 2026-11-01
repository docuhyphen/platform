package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "principal_group_member")
class PrincipalGroupMember
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "principal_group_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var principalGroupId: UUID

    @Column(name = "principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var principalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "principal_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var principalId: UUID

    @Column(name = "group_role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var groupRole: PrincipalGroupRoleName = PrincipalGroupRoleName.MEMBER

    @Column(name = "added_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var addedByAppUserId: UUID? = null

    @Column(name = "added_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var addedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    constructor()
}

