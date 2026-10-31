package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "information_request_template_requirement_binding")
class InformationRequestTemplateRequirementBinding
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "template_definition_id", nullable = false)
    lateinit var templateDefinitionId: UUID

    @Column(name = "template_requirement_id", nullable = false)
    lateinit var templateRequirementId: UUID

    @Column(name = "template_section_id", nullable = false)
    lateinit var templateSectionId: UUID

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 1

    @Column(name = "prompt", nullable = false, length = 1024)
    lateinit var prompt: String

    @Column(name = "help_text", nullable = true, length = 2048)
    var helpText: String? = null

    @Column(name = "response_mode", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var responseMode: InformationRequestResponseMode = InformationRequestResponseMode.PROVIDE

    @Column(name = "requiredness", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var requiredness: InformationRequestRequiredness = InformationRequestRequiredness.OPTIONAL

    @Column(name = "contributor_role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var contributorRole: InformationRequestContributorRole = InformationRequestContributorRole.CONTRIBUTOR

    @Column(name = "review_policy", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var reviewPolicy: InformationRequestReviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED

    @Column(name = "confidentiality_compartment_key", nullable = true, length = 64)
    var confidentialityCompartmentKey: String? = null

    @Column(name = "conditional_rule_key", nullable = true, length = 128)
    var conditionalRuleKey: String? = null

    @Column(name = "occurrence_anchor_key", nullable = true, length = 128)
    var occurrenceAnchorKey: String? = null

    @Column(name = "collected_field_definition_id", nullable = true)
    var collectedFieldDefinitionId: UUID? = null

    constructor()
}
