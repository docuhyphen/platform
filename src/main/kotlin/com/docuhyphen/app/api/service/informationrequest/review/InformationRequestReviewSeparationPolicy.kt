package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStagePlan
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestReviewSeparationPolicy @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val decisionRepository: InformationRequestReviewDecisionRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
)
{
    fun requireMayReview(
        snapshot: InformationRequestReviewSnapshot,
        stage: InformationRequestReviewStagePlan,
        principals: Set<PrincipalRef>,
    )
    {
        if (stage.excludesResponseParties && principals.any { it in responsePrincipals(snapshot) })
        {
            refuse("This review stage excludes anyone who answered, submitted, or attested the submission")
        }
        if (stage.excludesPriorReviewers && principals.any { it in priorReviewers(snapshot, stage) })
        {
            refuse("This review stage excludes anyone who already decided this submission")
        }
    }

    private fun responsePrincipals(snapshot: InformationRequestReviewSnapshot): Set<PrincipalRef>
    {
        val submission = snapshot.submission
        val recorded = submission.items.mapNotNull { item ->
            val kind = item.respondedByPrincipalKind ?: return@mapNotNull null
            val id = item.respondedByPrincipalId ?: return@mapNotNull null
            PrincipalRef(kind, id)
        } + PrincipalRef(
            submission.submissionPackage.submittedByPrincipalKind,
            submission.submissionPackage.submittedByPrincipalId
        ) +
                submission.attestations.map { PrincipalRef(it.principalKind, it.principalId) }
        val answering = partyRepository.findActiveForRequest(snapshot.review.informationRequestId)
            .filter { it.roleKey in ANSWERING_ROLES }
            .flatMap(::principalsOf)
        return recorded.toSet() + answering
    }

    private fun priorReviewers(
        snapshot: InformationRequestReviewSnapshot,
        stage: InformationRequestReviewStagePlan
    ): Set<PrincipalRef>
    {
        val earlier = snapshot.plan.stages.filter { it.position < stage.position }.map { it.stageKey }.toSet()
        val sameReview = snapshot.decisions
            .filter { it.stageKey in earlier && it.kind != InformationRequestReviewDecisionKind.CARRIED }
            .map { PrincipalRef(it.decidedByPrincipalKind, it.decidedByPrincipalId) }
        val reopened = snapshot.review.priorReviewId
            ?.takeIf {
                snapshot.review.kind == InformationRequestReviewKind.RECONSIDERATION ||
                        snapshot.review.kind == InformationRequestReviewKind.APPEAL
            }
            ?.let { decisionRepository.findForReview(it) }
            .orEmpty()
            .filter { it.kind != InformationRequestReviewDecisionKind.CARRIED }
            .map { PrincipalRef(it.decidedByPrincipalKind, it.decidedByPrincipalId) }
        return (sameReview + reopened).toSet()
    }

    private fun principalsOf(party: InformationRequestParty): Set<PrincipalRef> =
        requirementContext.principalsActingFor(party)

    private fun refuse(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, message)

    private companion object
    {
        val ANSWERING_ROLES = setOf(
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.PREPARER,
            InformationRequestShareRoleKey.ATTESTOR,
        )
    }
}
