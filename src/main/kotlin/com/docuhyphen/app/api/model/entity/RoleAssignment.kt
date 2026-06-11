package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Assigns a [RoleName] to either an [AppUser] or a [ServiceAccount] within a given scope.
 *
 * Scope semantics:
 * - [RoleScopeType.APP]              : `scopeId` is null; role is application-wide.
 * - [RoleScopeType.ORG]              : `scopeId` is an [Organization] id.
 * - [RoleScopeType.PRINCIPAL_GROUP]  : `scopeId` is a [PrincipalGroup] id.
 * - [RoleScopeType.RESOURCE]         : `scopeId` is the resource id (e.g. a [Exchange]).
 *
 * DDL enforces:
 * - APP scope <=> `scope_id IS NULL`
 * - exactly one of (`appUserId`, `serviceAccountId`) is set.
 */
@Entity
@Serializable
@Table(name = "role_assignment")
class RoleAssignment
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var appUserId: UUID? = null

    @Column(name = "service_account_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var serviceAccountId: UUID? = null

    @Column(name = "role_name", nullable = false, length = 64)
    var roleName: String = RoleName.END_USER.name

    @Column(name = "scope_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scopeType: RoleScopeType = RoleScopeType.APP

    @Column(name = "scope_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var scopeId: UUID? = null

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

