package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState

object InformationRequestReviewRouting
{
    private val REVIEW_ROUTED_EVIDENCE_STATES = setOf(
        InformationRequestEvidenceRequirementState.REVIEWABLE.name,
        InformationRequestEvidenceRequirementState.WAIVER_REQUESTED.name,
    )

    fun routes(
        reviewPolicy: InformationRequestReviewPolicy,
        requirementType: InformationRequestRequirementType,
        completenessState: InformationRequestCompletenessItemState,
        disposition: InformationRequestResponseDisposition?,
        evidenceState: String?,
    ): Boolean
    {
        if (completenessState != InformationRequestCompletenessItemState.COMPLETE) return false
        val byPolicy = when (reviewPolicy)
        {
            InformationRequestReviewPolicy.REQUIRED -> true
            InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION ->
                disposition != null &&
                    disposition != InformationRequestResponseDisposition.PROVIDED &&
                    disposition != InformationRequestResponseDisposition.NOT_ANSWERED
            InformationRequestReviewPolicy.NOT_REQUIRED -> false
        }
        return byPolicy ||
            (requirementType == InformationRequestRequirementType.DOCUMENT && evidenceState in REVIEW_ROUTED_EVIDENCE_STATES)
    }

    fun routes(item: InformationRequestSubmissionItem, reviewPolicy: InformationRequestReviewPolicy): Boolean =
        routes(reviewPolicy, item.requirementType, item.completenessState, item.disposition, item.evidenceState)
}
