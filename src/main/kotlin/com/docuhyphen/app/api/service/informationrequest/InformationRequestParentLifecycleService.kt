package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestParentLifecycleService @Inject constructor(
    private val requests: InformationRequestRepository,
    private val sessions: RequestAccessSessionService,
    private val history: InformationRequestTransitionHistoryService,
    private val shares: com.docuhyphen.app.api.service.exchange.ShareService,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun apply(exchangeId: UUID, status: ExchangeStatus, deleted: Boolean, actor: PrincipalRef)
    {
        val effects = InformationRequestTransitionMatrix.parentEffects(InformationRequestParentSnapshot(status, deleted, true))
        if (effects.nonTerminalRequestEffect == InformationRequestNonTerminalEffect.NONE) return
        requests.flushPendingChanges()
        requests.findForExchange(exchangeId).sortedBy { it.id }.forEach { candidate ->
            val request = requests.findRequestByIdForUpdate(candidate.id) ?: return@forEach
            request.ownerUserId?.let { shares.retainInformationRequestOwnerRead(request.id, it) }
            if (effects.externalSessionEffect in setOf(InformationRequestExternalSessionEffect.REVOKE_ALL,
                    InformationRequestExternalSessionEffect.REVOKE_NON_OWNER))
                sessions.revokeAllForRequest(request.id)
            if (effects.nonTerminalRequestEffect == InformationRequestNonTerminalEffect.CANCEL && !request.state.isTerminal)
            {
                val fromState = request.state
                val now = Timestamp.from(Instant.now())
                request.state = InformationRequestState.CANCELLED
                request.cancelledAt = now
                request.updatedAt = now
                request.aggregateRevision += 1
                requests.update(request)
                history.record(InformationRequestTransitionHistoryCommand(request, fromState, request.state,
                    InformationRequestMutation.CANCEL, actor, reasonCode = "PARENT_${status.name}"))
            }
        }
    }
}
