package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionStages
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestSatisfactionService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val lockService: InformationRequestSubmissionLockService,
    private val stages: InformationRequestSubmissionStages,
    private val reviewRepository: InformationRequestReviewRepository,
    private val requestRepository: InformationRequestRepository,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    fun isSatisfied(requestId: UUID): Boolean
    {
        val request = requestRepository.findById(requestId) ?: return false
        val active = lockService.activePackages(requestId)
        val scopes: List<String?> = stages.stageOrder(request).ifEmpty { listOf(null) }
        val reviews = reviewRepository.findForRequest(requestId)
        return scopes.all { scope ->
            val current = active.firstOrNull { it.stageKey == scope } ?: return@all false
            !current.reviewRequired ||
                reviews.filter { it.packageId == current.id }.maxByOrNull { it.reviewNumber }?.state?.accepted == true
        }
    }

    fun closeIfSatisfied(
        locked: LockedInformationRequest,
        completingPackageId: UUID,
        actor: PrincipalRef,
        idempotencyKey: String,
    ): Boolean
    {
        val request = locked.request
        if (request.state.isTerminal || !isSatisfied(request.id)) return false
        val previousState = request.state
        val nextState = requireNotNull(gate.requireMutation(locked, InformationRequestMutation.CLOSE)) {
            "Closing an Information Request names the closed state"
        }
        val now = Timestamp.from(clock.instant())
        request.state = nextState
        request.closedAt = now
        request.satisfiedAt = now
        request.satisfiedByPackageId = completingPackageId
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = previousState,
                toState = nextState,
                mutation = InformationRequestMutation.CLOSE,
                actor = actor,
                idempotencyKey = "information_request.closure|${request.id}|$idempotencyKey",
                details = mapOf("satisfiedByPackageId" to completingPackageId.toString()),
            ),
        )
        return true
    }
}
