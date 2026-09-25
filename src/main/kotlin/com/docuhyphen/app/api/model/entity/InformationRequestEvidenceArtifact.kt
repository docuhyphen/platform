package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * One logical evidence item collected for a single runtime Requirement occurrence. The artifact
 * holds identity and the optimistic revision; the content of each attempt lives in its versions.
 */
@Entity
@Table(name = "information_request_evidence_artifact")
class InformationRequestEvidenceArtifact
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "artifact_key", nullable = false, length = 128)
    lateinit var artifactKey: String

    @Column(name = "artifact_revision", nullable = false)
    var artifactRevision: Long = 1

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var createdByPrincipalKind: PrincipalKind

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "collection_state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var collectionState: InformationRequestEvidenceCollectionState = InformationRequestEvidenceCollectionState.ACTIVE

    @Column(name = "state_changed_at")
    var stateChangedAt: Timestamp? = null

    @Column(name = "state_changed_by_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var stateChangedByPrincipalKind: PrincipalKind? = null

    @Column(name = "state_changed_by_principal_id")
    var stateChangedByPrincipalId: UUID? = null

    @Column(name = "state_reason", length = 512)
    var stateReason: String? = null

    constructor()
}
