package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One value an [InformationRequestTemplateEvidencePolicy] accepts for one attribute of a requested
 * Document. An attribute with no accepted values recorded is unrestricted, and the same value under
 * two attributes is two different restrictions.
 *
 * A restriction can only be applied to an attribute the policy captures, so a Version that restricts
 * one it never asks for is refused when it freezes. The Version is carried alongside the policy so
 * the set freezes with the configuration that holds it.
 */
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

    /** Authored content in the owner's own vocabulary, never interpreted by production logic. */
    @Column(name = "accepted_value", nullable = false, length = 255)
    lateinit var acceptedValue: String

    constructor()
}
