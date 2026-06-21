package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class VariableScope { ORG, PERSONAL }

@Entity
@Serializable
@Table(name = "variable_definition")
class VariableDefinition
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "key", nullable = false, length = 64)
    lateinit var key: String

    @Column(name = "default_value", nullable = true, columnDefinition = "text")
    var defaultValue: String? = null

    @Column(name = "scope", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var scope: VariableScope = VariableScope.PERSONAL

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "created_by_app_user_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var createdByAppUserId: UUID

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
