package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestScheduledContinuation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.parent.lockExchangesInOrder
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangesOf
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestMutationGate @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeLookup: ExchangeLookupService,
    private val authorizationService: AuthorizationService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val continuationGuard: InformationRequestContinuationGuard,
)
{
    fun lock(requestId: UUID): LockedInformationRequest
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeLookup)
        return LockedInformationRequest(exchange, lockRequest(requestId))
    }

    fun requireVisibleTarget(access: RequestAccessContext, requestId: UUID) {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, access)
    }

    fun lockAll(requestIds: Collection<UUID>): List<LockedInformationRequest> {
        val ordered = requestIds.distinct().sorted()
        val exchanges = lockParentExchangesOf(ordered, requestRepository, exchangeLookup)
        return ordered.map { requestId ->
            val request = lockRequest(requestId)
            LockedInformationRequest(exchanges.getValue(request.exchangeId), request)
        }
    }

    fun lockExisting(requestIds: Collection<UUID>): List<LockedInformationRequest> {
        val parents = requestRepository.findParentExchangeIds(requestIds.distinct())
        val exchanges = lockExchangesInOrder(parents.values, exchangeLookup)
        return parents.keys.sorted().mapNotNull { requestId ->
            requestRepository.findRequestByIdForUpdate(requestId)
                ?.let { LockedInformationRequest(exchanges.getValue(it.exchangeId), it) }
        }
    }

    private fun lockRequest(requestId: UUID): InformationRequest =
        requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )

    fun requireMutation(
        locked: LockedInformationRequest,
        mutation: InformationRequestMutation
    ): InformationRequestState?
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = locked.exchange.status,
                deleted = locked.exchange.isDeleted,
                lockedForUpdate = true,
            ),
            locked.request.state,
            mutation,
        )
        return when (decision)
        {
            is InformationRequestPolicyDecision.Allow -> decision.nextState
            is InformationRequestPolicyDecision.Deny -> throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request mutation $mutation is not allowed in the request's current state",
            )
        }
    }

    fun permitsMutation(
        request: InformationRequest,
        mutation: InformationRequestMutation,
        continuation: InformationRequestContinuationAction,
    ): Boolean {
        val exchange = exchangeLookup.findExchange(request.exchangeId) ?: return false
        return InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(exchange.status, exchange.isDeleted, true), request.state, mutation,
        ) is InformationRequestPolicyDecision.Allow &&
                scheduledContinuationOf(
                    LockedInformationRequest(exchange, request),
                    continuation
                ) == InformationRequestScheduledContinuation.PROCEEDS
    }

    fun requireContinuation(locked: LockedInformationRequest, action: InformationRequestContinuationAction)
    {
        continuationGuard.requireContinuation(locked, action)
    }

    fun requireOwnerRecovery(locked: LockedInformationRequest, action: InformationRequestContinuationAction) {
        continuationGuard.requireOwnerRecovery(locked, action)
    }

    fun requireOwnerRecovery(locked: LockedInformationRequest) {
        continuationGuard.requireOwnerRecovery(locked)
    }

    fun requireOwnerWithdrawal(locked: LockedInformationRequest) {
        continuationGuard.requireOwnerWithdrawal(locked)
    }

    fun scheduledContinuationOf(
        locked: LockedInformationRequest,
        action: InformationRequestContinuationAction,
    ): InformationRequestScheduledContinuation = continuationGuard.scheduledContinuationOf(locked, action)

    fun requireExpansionEntitlement(locked: LockedInformationRequest) {
        entitlementGuard.requireRequestMutation(locked.exchange)
    }

    fun requireNotOperationallySuspended(locked: LockedInformationRequest) {
        entitlementGuard.requireNotOperationallySuspended(locked.exchange)
    }

    fun permitsRequest(access: RequestAccessContext, action: Action, requestId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            action,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        ) is Decision.Allow

    fun permitsRequirement(access: RequestAccessContext, action: Action, requirementId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            action,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) is Decision.Allow

    fun authorizeRequest(access: RequestAccessContext, actions: List<Action>, requestId: UUID)
    {
        if (actions.none { permitsRequest(access, it, requestId) })
        {
            throw ForbiddenException("Access denied to this Information Request")
        }
    }

    fun authorizeRequirement(access: RequestAccessContext, action: Action, requirementId: UUID)
    {
        if (!permitsRequirement(access, action, requirementId))
        {
            throw ForbiddenException("Access denied to this Information Request Requirement")
        }
    }
}
