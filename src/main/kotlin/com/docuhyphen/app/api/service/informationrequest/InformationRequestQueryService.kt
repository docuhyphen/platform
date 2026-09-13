package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Reads of runtime Information Requests, shared by every access surface. A caller is always given a
 * [RequestAccessContext] built at the edge; this service never distinguishes an authenticated caller
 * from a no-auth respondent, since both surfaces build the exact same context shape.
 */
@ApplicationScoped
class InformationRequestQueryService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val requestRepository: InformationRequestRepository,
    private val authorizationService: AuthorizationService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
)
{
    fun listForExchange(exchangeId: UUID, access: RequestAccessContext): List<InformationRequest>
    {
        val exchange = exchangeRepository.findById(exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")

        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.exchange(exchangeId),
            access.authorization,
        )
        val visible = requestRepository.findForExchange(exchangeId).filter {
            authorizationService.authorize(access.principal, Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(it.id), access.authorization) !is Decision.Deny
        }
        if (decision is Decision.Deny && visible.isEmpty())
        {
            throw ForbiddenException("Access denied to list Information Requests")
        }
        val readable = visible.filter { readEntitlementAllowed(it, exchange) }
        if (visible.isNotEmpty() && readable.isEmpty())
        {
            requireReadEntitlement(visible.first(), exchange)
        }

        return readable
    }

    fun findById(requestId: UUID, access: RequestAccessContext): InformationRequest
    {
        val request = requestRepository.findById(requestId)
            ?: throw IllegalArgumentException("Information Request not found")

        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to view this Information Request")
        }

        val exchange = exchangeRepository.findById(request.exchangeId)
            ?: throw IllegalStateException("Information Request $requestId has no parent Exchange")
        requireReadEntitlement(request, exchange)

        return request
    }

    private fun readEntitlementAllowed(request: InformationRequest, exchange: Exchange): Boolean =
        try
        {
            requireReadEntitlement(request, exchange)
            true
        }
        catch (_: SubscriptionDenialException)
        {
            false
        }
        catch (_: InformationRequestLifecycleException)
        {
            false
        }

    private fun requireReadEntitlement(request: InformationRequest, exchange: Exchange)
    {
        val grant = executionGrantService.findForRequest(request.id)
        if (grant == null)
        {
            entitlementGuard.requireRequestAccess(exchange)
        }
        else
        {
            entitlementGuard.requireNotOperationallySuspended(exchange)
            requireGrantNotRevoked(grant)
        }
    }

    private fun requireGrantNotRevoked(grant: RequestExecutionGrant)
    {
        if (grant.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
    }
}
