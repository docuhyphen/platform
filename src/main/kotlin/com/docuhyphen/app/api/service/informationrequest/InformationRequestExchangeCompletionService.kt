package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

class InformationRequestExchangeCompletionException(
    reasonCode: String,
    val requestIds: List<UUID>,
) : InformationRequestLifecycleException(reasonCode, "This Exchange cannot end while its Information Requests are open")

@ApplicationScoped
class InformationRequestExchangeCompletionService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional(value = Transactional.TxType.MANDATORY, dontRollbackOn = [InformationRequestExchangeCompletionException::class])
    fun prepareEnding(exchange: Exchange, actor: PrincipalRef, cancelRemaining: Boolean)
    {
        requestRepository.flushPendingChanges()
        val requests = requestRepository.findForExchange(exchange.id)
            .sortedBy { it.id }
            .mapNotNull { requestRepository.findRequestByIdForUpdate(it.id) }
        if (requests.isEmpty()) return
        val parent = InformationRequestParentSnapshot(exchange.status, exchange.isDeleted, lockedForUpdate = true)
        val decision = InformationRequestTransitionMatrix.canEndExchange(parent, requests.map(::candidateOf))
        if (decision is InformationRequestPolicyDecision.Allow) return
        val reasonCode = (decision as InformationRequestPolicyDecision.Deny).reasonCode
        when (reasonCode)
        {
            InformationRequestErrorCatalog.COMPLETION_GATES_UNSATISFIED ->
                throw InformationRequestExchangeCompletionException(
                    reasonCode,
                    requests.filter { it.gatesExchangeClosure && !it.state.isTerminal }.map { it.id },
                )
            InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION ->
            {
                val remaining = requests.filter { !it.gatesExchangeClosure && !it.state.isTerminal }
                if (!cancelRemaining) throw InformationRequestExchangeCompletionException(reasonCode, remaining.map { it.id })
                remaining.forEach { cancel(it, actor) }
            }
            else -> throw InformationRequestExchangeCompletionException(reasonCode, emptyList())
        }
    }

    private fun cancel(request: InformationRequest, actor: PrincipalRef)
    {
        val fromState = request.state
        val now = Timestamp.from(clock.instant())
        request.state = InformationRequestState.CANCELLED
        request.cancelledAt = now
        request.updatedAt = now
        request.aggregateRevision += 1
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = fromState,
                toState = request.state,
                mutation = InformationRequestMutation.CANCEL,
                actor = actor,
                reasonCode = EXCHANGE_ENDED,
                idempotencyKey = "information_request.exchange_ending|${request.id}",
            ),
        )
    }

    private fun candidateOf(request: InformationRequest) =
        InformationRequestCompletionCandidate(request.state, request.gatesExchangeClosure)

    private companion object
    {
        const val EXCHANGE_ENDED = "EXCHANGE_ENDED"
    }
}
