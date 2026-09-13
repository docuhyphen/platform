package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Places one stable [InformationRequestTemplateRequirement] into one
 * [InformationRequestTemplateVersion], in one [InformationRequestTemplateSection], at one position,
 * and states the policy under which that Version asks for it.
 *
 * The owning definition is carried alongside the two parents so storage can hold them in agreement:
 * the section must belong to this Version and the requirement to this Version's definition, so no
 * Version can hold another owner's requirement.
 *
 * Everything here is restatable by a later Version, unlike the requirement's own type and key. The
 * permitted answers and the supporting evidence of a binding are sets, so they are held by
 * [InformationRequestTemplateBindingDisposition] and
 * [InformationRequestTemplateBindingEvidenceLink].
 */
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

    /** The wording this Version uses to ask. Authored content, never interpreted by production logic. */
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

    /**
     * Key into the owner's own compartment vocabulary. Null means the request's own access rules are
     * the only ones that apply to this response.
     */
    @Column(name = "confidentiality_compartment_key", nullable = true, length = 64)
    var confidentialityCompartmentKey: String? = null

    /** The versioned rule that decides whether this requirement applies. Required when conditional. */
    @Column(name = "conditional_rule_key", nullable = true, length = 128)
    var conditionalRuleKey: String? = null

    /** The repeatable group this requirement is answered once per. Null means once for the request. */
    @Column(name = "occurrence_anchor_key", nullable = true, length = 128)
    var occurrenceAnchorKey: String? = null

    /**
     * The stable Field Definition a typed answer is recorded against. Present exactly when the
     * requirement asks for typed data. The stable identity is named rather than a contract version,
     * because that is what survives both a new Schema Version and a new Template Version.
     */
    @Column(name = "collected_field_definition_id", nullable = true)
    var collectedFieldDefinitionId: UUID? = null

    constructor()
}
