package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDelegationCandidate
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestReviewDelegateCandidates @Inject constructor(
    private val partyQueries: InformationRequestPartyQueryService,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val separation: InformationRequestReviewSeparationPolicy,
    private val principalDisplay: PrincipalDisplayService,
) {
    fun candidatesFor(
        snapshot: InformationRequestReviewSnapshot,
        assignment: InformationRequestReviewAssignment,
    ): List<InformationRequestReviewDelegationCandidate> {
        if (assignment.state != InformationRequestReviewAssignmentState.ACTIVE || assignment.decidedAt != null) return emptyList()
        if (snapshot.review.state.settled) return emptyList()
        if (snapshot.standing.stage(assignment.stageKey)?.state == InformationRequestReviewStageState.SETTLED) return emptyList()
        val stage = snapshot.plan.stage(assignment.stageKey) ?: return emptyList()
        return partyQueries.partiesOf(snapshot.review.informationRequestId)
            .filter { party ->
                party.active && party.roleKey == InformationRequestShareRoleKey.REVIEWER &&
                        party.principalKind != null && party.principalKind != PrincipalKind.PARTICIPANT &&
                        party.principalId != null
            }
            .filterNot { party ->
                InformationRequestReviewStageMembership.holds(
                    snapshot.assignments,
                    stage.stageKey,
                    party
                )
            }
            .filter { party ->
                separation.permitsReview(
                    snapshot,
                    stage,
                    requirementContext.principalsActingFor(party)
                )
            }
            .mapNotNull { party ->
                val principal = PrincipalRef(requireNotNull(party.principalKind), requireNotNull(party.principalId))
                principalDisplay.display(principal).name?.let { label ->
                    InformationRequestReviewDelegationCandidate(party.id, label)
                }
            }
    }
}
