package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestReviewPartyRevocation @Inject constructor(
    private val reviews: InformationRequestReviewRepository,
    private val assignments: InformationRequestReviewAssignmentRepository,
    private val settlement: InformationRequestReviewSettlement,
    private val history: InformationRequestTransitionHistoryService,
    private val clock: Clock,
) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun revoke(
        locked: LockedInformationRequest,
        partyId: UUID,
        actor: InformationRequestAuditActor,
        reasonCode: String,
        idempotencyKey: String,
    ) {
        if (locked.request.state.isTerminal) return
        val now = Timestamp.from(clock.instant())
        reviews.findForRequest(locked.request.id).filterNot { it.state.settled }.forEach { review ->
            val revoked = assignments.findForReview(review.id).filter {
                it.reviewerPartyId == partyId && it.state == InformationRequestReviewAssignmentState.ACTIVE && it.decidedAt == null
            }
            if (revoked.isEmpty()) return@forEach
            revoked.forEach { assignment ->
                assignment.state = InformationRequestReviewAssignmentState.REVOKED
                assignment.changeReasonCode = reasonCode
                assignment.changedByPrincipalKind = actor.principal.kind
                assignment.changedByPrincipalId = actor.principal.id
                assignment.changedAt = now
                assignments.update(assignment)
                history.record(
                    InformationRequestTransitionHistoryCommand(
                        request = locked.request,
                        fromState = locked.request.state,
                        toState = locked.request.state,
                        mutation = InformationRequestMutation.ASSIGN_REVIEWER,
                        actor = actor,
                        reasonCode = reasonCode,
                        partyId = partyId,
                        auditIdempotencyKey = "information_request.review_party_revoke|${assignment.id}|$idempotencyKey",
                        details = mapOf(
                            "reviewId" to review.id.toString(),
                            "assignmentId" to assignment.id.toString(),
                            "reviewerPartyId" to partyId.toString(),
                            "stageKey" to assignment.stageKey,
                            "assignmentChange" to "REVOCATION"
                        ),
                    )
                )
            }
            review.reviewRevision += 1
            reviews.update(review)
            settlement.settleIfDecided(locked, review, actor, idempotencyKey)
        }
    }
}