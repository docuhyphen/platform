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
 * Append-only lineage from an obsolete subject identity to its unique current successor.
 */
@Entity
@Serializable
@Table(name = "subject_identity_transition")
class SubjectIdentityTransition
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "source_subject_identity_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var sourceSubjectIdentityId: UUID

    @Column(name = "target_subject_identity_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var targetSubjectIdentityId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 32)
    lateinit var ownerType: SubjectIdentityOwnerType

    @Column(name = "owner_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var ownerId: UUID

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_kind", nullable = false, length = 32)
    lateinit var transitionKind: SubjectIdentityTransitionKind

    @Column(name = "reason", length = 1000)
    var reason: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    lateinit var recordedByPrincipalKind: PrincipalKind

    @Column(name = "recorded_by_principal_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var recordedByPrincipalId: UUID

    @Column(name = "recorded_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var recordedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
