package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestMutationGate @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeRepository: ExchangeRepository,
    private val authorizationService: AuthorizationService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
)
{
    fun lock(requestId: UUID): LockedInformationRequest
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        return LockedInformationRequest(exchange, request)
    }

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

    fun requireContinuationEntitlement(locked: LockedInformationRequest)
    {
        val grant = executionGrantService.findForRequest(locked.request.id)
        if (grant == null)
        {
            entitlementGuard.requireRequestMutation(locked.exchange)
            return
        }
        entitlementGuard.requireNotOperationallySuspended(locked.exchange)
        if (grant.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
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
