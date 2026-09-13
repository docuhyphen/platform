package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Current revision pointer for a runtime Requirement occurrence. Advancing this row does not
 * rewrite the append-only revisions it points at.
 */
@Entity
@Table(name = "information_request_requirement_current")
class InformationRequestRequirementCurrent
{
    @Id
    @Column(name = "information_request_requirement_id")
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "current_revision_id", nullable = false)
    lateinit var currentRevisionId: UUID

    @Column(name = "current_revision_number", nullable = false)
    var currentRevisionNumber: Int = 1

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
