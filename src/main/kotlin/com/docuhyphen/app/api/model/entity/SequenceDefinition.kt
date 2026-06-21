package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class SequenceResetPeriod { NEVER, YEARLY, MONTHLY }

@Entity
@Serializable
@Table(name = "sequence_definition")
class SequenceDefinition
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var organizationId: UUID

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "key", nullable = false, length = 64)
    lateinit var key: String

    @Column(name = "current_value", nullable = false)
    var currentValue: Long = 0L

    @Column(name = "pad_width", nullable = false)
    var padWidth: Int = 0

    @Column(name = "prefix", nullable = true, length = 64)
    var prefix: String? = null

    @Column(name = "suffix", nullable = true, length = 64)
    var suffix: String? = null

    @Column(name = "reset_period", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var resetPeriod: SequenceResetPeriod = SequenceResetPeriod.NEVER

    @Column(name = "last_reset_at", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var lastResetAt: Timestamp? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @Column(name = "created_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
