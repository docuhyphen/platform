package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "information_request_template_review_stage")
class InformationRequestTemplateReviewStage
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "stage_key", nullable = false, length = 128)
    lateinit var stageKey: String

    @Column(name = "position", nullable = false)
    var position: Int = 1

    @Column(name = "title", nullable = false, length = 255)
    lateinit var title: String

    @Column(name = "aggregation", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var aggregation: InformationRequestReviewAggregation = InformationRequestReviewAggregation.ANY

    @Column(name = "quorum_count")
    var quorumCount: Int? = null

    @Column(name = "minimum_reviewer_count", nullable = false)
    var minimumReviewerCount: Int = 1

    @Column(name = "tie_resolution", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var tieResolution: InformationRequestReviewTieResolution = InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME

    @Column(name = "override_permitted", nullable = false)
    var overridePermitted: Boolean = false

    @Column(name = "excludes_response_parties", nullable = false)
    var excludesResponseParties: Boolean = false

    @Column(name = "excludes_prior_reviewers", nullable = false)
    var excludesPriorReviewers: Boolean = false

    constructor()
}

@Entity
@Table(name = "information_request_template_review_stage_section")
class InformationRequestTemplateReviewStageSection
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "review_stage_id", nullable = false)
    lateinit var reviewStageId: UUID

    @Column(name = "template_section_id", nullable = false)
    lateinit var templateSectionId: UUID

    constructor()
}
