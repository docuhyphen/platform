package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "information_request_template_evidence_accepted_value")
class InformationRequestTemplateEvidenceAcceptedValue
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "evidence_policy_id", nullable = false)
    lateinit var evidencePolicyId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "attribute", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var attribute: InformationRequestEvidenceAttribute

    @Column(name = "accepted_value", nullable = false, length = 255)
    lateinit var acceptedValue: String

    constructor()
}
