package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStagePlan
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestReviewSeparationPolicy @Inject constructor(
    private val partyQueries: InformationRequestPartyQueryService,
    private val decisionRepository: InformationRequestReviewDecisionRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val evidenceQueries: InformationRequestEvidenceQueryService,
)
{
    fun requireMayReview(
        snapshot: InformationRequestReviewSnapshot,
        stage: InformationRequestReviewStagePlan,
        principals: Set<PrincipalRef>,
    )
    {
        refusalOf(snapshot, stage, principals)?.let(::refuse)
    }

    fun permitsReview(
        snapshot: InformationRequestReviewSnapshot,
        stage: InformationRequestReviewStagePlan,
        principals: Set<PrincipalRef>,
    ): Boolean = refusalOf(snapshot, stage, principals) == null

    private fun refusalOf(
        snapshot: InformationRequestReviewSnapshot,
        stage: InformationRequestReviewStagePlan,
        principals: Set<PrincipalRef>,
    ): String? = when {
        snapshot.assignments.any { assignment ->
            assignment.stageKey == stage.stageKey && assignment.state == InformationRequestReviewAssignmentState.RECUSED &&
                    (PrincipalRef(assignment.reviewerPrincipalKind, assignment.reviewerPrincipalId) in principals ||
                            partyQueries.findParty(assignment.reviewerPartyId)
                                ?.let { party -> principalsOf(party).any { it in principals } } == true)
        } -> "This reviewer declared a conflict for this review stage"

        stage.excludesResponseParties && principals.any { it in responsePrincipals(snapshot) } ->
            "This review stage excludes anyone who answered, submitted, attested, or supplied evidence for the submission"

        stage.excludesPriorReviewers && principals.any { it in priorReviewers(snapshot, stage) } ->
            "This review stage excludes anyone who already decided this submission"

        else -> null
    }

    private fun responsePrincipals(snapshot: InformationRequestReviewSnapshot): Set<PrincipalRef>
    {
        val submission = snapshot.submission
        val recorded = (submission.items.mapNotNull { item ->
            val kind = item.respondedByPrincipalKind ?: return@mapNotNull null
            val id = item.respondedByPrincipalId ?: return@mapNotNull null
            PrincipalRef(kind, id)
        } + PrincipalRef(
            submission.submissionPackage.submittedByPrincipalKind,
            submission.submissionPackage.submittedByPrincipalId
        ) +
                submission.attestations.map { PrincipalRef(it.principalKind, it.principalId) } +
                evidenceQueries.versionRecords(submission.evidence.map { it.evidenceVersionId }).values.map { it.createdBy })
            .toSet()
            .let { principals -> principals + principals.flatMap(requirementContext::linkedAccountPrincipalsOf) }
        val attestingPartyIds = submission.attestations.map { it.partyId }.toSet()
        val responding = partyQueries.partiesOf(snapshot.review.informationRequestId)
            .filter { it.roleKey in InformationRequestShareRoleKey.RESPONDING_ROLES }
            .map { party -> party to principalsOf(party) }
            .filter { (party, principals) ->
                party.active || party.id in attestingPartyIds || principals.any { it in recorded }
            }
            .flatMap { (_, principals) -> principals }
        return recorded + responding
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
}
