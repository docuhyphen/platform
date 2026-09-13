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
 * An explicitly authorized external alias for an opaque subject identity. The alias never becomes
 * the subject's primary key and is unique only inside its owning tenant.
 */
@Entity
@Serializable
@Table(name = "subject_identity_external_identifier")
class SubjectIdentityExternalIdentifier
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "subject_identity_ref_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var subjectIdentityRefId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: SubjectIdentityOwnerType

    @Column(name = "owner_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var ownerId: UUID

    @Column(name = "authority", nullable = false, length = 160)
    lateinit var authority: String

    @Column(name = "identifier_type", nullable = false, length = 96)
    lateinit var identifierType: String

    @Column(name = "identifier_value", nullable = false, length = 512)
    lateinit var identifierValue: String

    @Enumerated(EnumType.STRING)
    @Column(name = "authorized_by_principal_kind", nullable = false, length = 32)
    lateinit var authorizedByPrincipalKind: PrincipalKind

    @Column(name = "authorized_by_principal_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var authorizedByPrincipalId: UUID

    @Column(name = "authorized_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var authorizedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
