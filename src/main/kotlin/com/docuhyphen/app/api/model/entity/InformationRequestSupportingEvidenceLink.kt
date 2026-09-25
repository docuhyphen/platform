package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "information_request_supporting_evidence_link")
class InformationRequestSupportingEvidenceLink
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "supported_requirement_id", nullable = false)
    lateinit var supportedRequirementId: UUID

    @Column(name = "supporting_requirement_id", nullable = false)
    lateinit var supportingRequirementId: UUID

    @Column(name = "template_evidence_link_id", nullable = false)
    lateinit var templateEvidenceLinkId: UUID

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
