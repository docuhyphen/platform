package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestFirstViewService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun recordIfFirst(requestId: UUID, access: RequestAccessContext): Boolean
    {
        val candidate = requestRepository.findById(requestId) ?: return false
        if (candidate.firstViewedAt != null || !isRespondingParty(requestId, access)) return false
        val locked = gate.lock(requestId)
        val request = locked.request
        if (request.firstViewedAt != null) return false
        val permitted = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(locked.exchange.status, locked.exchange.isDeleted, lockedForUpdate = true),
            request.state,
            InformationRequestMutation.RECORD_FIRST_VIEW,
        )
        if (permitted is InformationRequestPolicyDecision.Deny) return false
        val now = Timestamp.from(clock.instant())
        request.firstViewedAt = now
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.RECORD_FIRST_VIEW,
                actor = access.principal,
                idempotencyKey = "information_request.first_view|$requestId",
            ),
        )
        return true
    }

    private fun isRespondingParty(requestId: UUID, access: RequestAccessContext): Boolean =
        partyRepository.findActiveForRequest(requestId)
            .filter { it.roleKey in RESPONDING_ROLES }
            .any { access.principal in requirementContext.principalsActingFor(it) }

    private companion object
    {
        val RESPONDING_ROLES = setOf(
            InformationRequestShareRoleKey.SUBJECT,
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.PREPARER,
            InformationRequestShareRoleKey.ATTESTOR,
        )
    }
}
