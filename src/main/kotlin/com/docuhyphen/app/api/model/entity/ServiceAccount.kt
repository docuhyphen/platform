package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "service_account")
class ServiceAccount
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "scopes", nullable = true, length = 2048)
    var scopes: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_by", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdBy: UUID? = null

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

