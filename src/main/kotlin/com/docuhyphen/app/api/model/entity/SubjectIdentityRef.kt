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

/**
 * Stable, opaque identity for what an Information Request concerns. Mutable names, contact details,
 * and external identifiers are deliberately stored outside this primary identity.
 */
@Entity
@Serializable
@Table(name = "subject_identity_ref")
class SubjectIdentityRef
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: SubjectIdentityOwnerType

    @Column(name = "owner_organization_id")
    @Serializable(with = UUIDSerializer::class)
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_user_id")
    @Serializable(with = UUIDSerializer::class)
    var ownerUserId: UUID? = null

    @Column(name = "owner_id", insertable = false, updatable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var ownerId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_kind", nullable = false, length = 32)
    lateinit var subjectKind: SubjectKind

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
