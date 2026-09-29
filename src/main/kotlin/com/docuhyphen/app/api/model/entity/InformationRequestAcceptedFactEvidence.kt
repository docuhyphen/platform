package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "information_request_accepted_fact_evidence")
class InformationRequestAcceptedFactEvidence
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "fact_id", nullable = false)
    lateinit var factId: UUID

    @Column(name = "source_submission_evidence_id", nullable = false)
    lateinit var sourceSubmissionEvidenceId: UUID

    @Column(name = "evidence_version_id", nullable = false)
    lateinit var evidenceVersionId: UUID
}
