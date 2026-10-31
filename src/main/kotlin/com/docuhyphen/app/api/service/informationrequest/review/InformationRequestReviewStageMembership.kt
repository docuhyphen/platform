package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState

object InformationRequestReviewStageMembership {
    fun holds(
        assignments: List<InformationRequestReviewAssignment>,
        stageKey: String,
        party: InformationRequestParty,
    ): Boolean =
        assignments.any {
            it.state == InformationRequestReviewAssignmentState.ACTIVE && it.stageKey == stageKey &&
                    (it.reviewerPartyId == party.id ||
                            (it.reviewerPrincipalKind == party.principalKind && it.reviewerPrincipalId == party.principalId))
        }
}
