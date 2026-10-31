package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockStopper
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

class InformationRequestExchangeCompletionException(
    reasonCode: String,
    val requestIds: List<UUID>,
) : InformationRequestLifecycleException(reasonCode, "This Exchange cannot end while its Information Requests are open")

@ApplicationScoped
class InformationRequestExchangeCompletionService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clockStopper: InformationRequestClockStopper,
    private val bootstrapLinks: InformationRequestBootstrapShareLinkService,
    private val gate: InformationRequestMutationGate,
    private val clock: Clock,
)
{
    @Transactional(
        value = Transactional.TxType.MANDATORY,
        dontRollbackOn = [InformationRequestExchangeCompletionException::class]
    )
    fun checkEnding(exchange: Exchange, cancelRemaining: Boolean, access: RequestAccessContext? = null) {
        remainingToCancel(exchange, cancelRemaining, access)
    }

    @Transactional(
        value = Transactional.TxType.MANDATORY,
        dontRollbackOn = [InformationRequestExchangeCompletionException::class]
    )
    fun prepareEnding(
        exchange: Exchange,
        actor: InformationRequestAuditActor,
        cancelRemaining: Boolean,
        access: RequestAccessContext? = null,
        cancelAuthorityCheckedAt: Instant? = null,
    )
    {
        remainingToCancel(exchange, cancelRemaining, access, cancelAuthorityCheckedAt).forEach { cancel(it, actor) }
    }

    private fun remainingToCancel(
        exchange: Exchange,
        cancelRemaining: Boolean,
        access: RequestAccessContext?,
        cancelAuthorityCheckedAt: Instant? = null,
    ): List<InformationRequest> {
        val visible = { request: InformationRequest ->
            access == null || gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, request.id)
        }
        requestRepository.flushPendingChanges()
        val requests = requestRepository.findForExchange(exchange.id)
            .sortedBy { it.id }
            .mapNotNull { requestRepository.findRequestByIdForUpdate(it.id) }
        if (requests.isEmpty()) return emptyList()
        val parent = InformationRequestParentSnapshot(exchange.status, exchange.isDeleted, lockedForUpdate = true)
        val decision = InformationRequestTransitionMatrix.canEndExchange(parent, requests.map(::candidateOf))
        if (decision is InformationRequestPolicyDecision.Allow) return emptyList()
        return when (val reasonCode = (decision as InformationRequestPolicyDecision.Deny).reasonCode)
        {
            InformationRequestErrorCatalog.COMPLETION_GATES_UNSATISFIED ->
                throw InformationRequestExchangeCompletionException(
                    reasonCode,
                    requests.filter { it.gatesExchangeClosure && !it.state.isTerminal && visible(it) }.map { it.id },
                )

            InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION ->
            {
                val remaining = requests.filter { !it.gatesExchangeClosure && !it.state.isTerminal }
                if (!cancelRemaining) throw InformationRequestExchangeCompletionException(
                    reasonCode,
                    remaining.filter(visible).map { it.id })
                if (access != null && remaining.any {
                        !gate.permitsRequest(
                            access,
                            Action.INFORMATION_REQUEST_CANCEL,
                            it.id
                        )
                    } ||
                    cancelAuthorityCheckedAt != null && remaining.any {
                        it.createdAt.toInstant().isAfter(cancelAuthorityCheckedAt)
                    }
                ) {
                    throw InformationRequestExchangeCompletionException(
                        InformationRequestErrorCatalog.REMAINING_REQUESTS_NOT_CANCELLABLE,
                        remaining.filter(visible).map { it.id },
                    )
                }
                remaining
            }

            else -> throw InformationRequestExchangeCompletionException(reasonCode, emptyList())
        }
    }

    private fun cancel(request: InformationRequest, actor: InformationRequestAuditActor)
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
                auditIdempotencyKey = "information_request.exchange_ending|${request.id}",
                requireAuditCapture = true,
            ),
        )
        bootstrapLinks.revokeLinksKeepingSessions(
            request,
            InformationRequestCredentialCutoff(actor, InformationRequestCredentialCutoff.REQUEST_CANCELLED),
        )
        clockStopper.stopForFinishedRequest(
            request,
            actor.principal,
            now.toInstant(),
            InformationRequestClockStopper.PARENT_FINISHED,
        )
    }

    private fun candidateOf(request: InformationRequest) =
        InformationRequestCompletionCandidate(request.state, request.gatesExchangeClosure)

    private companion object
    {
        const val EXCHANGE_ENDED = "EXCHANGE_ENDED"
    }
}
