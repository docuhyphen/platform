package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
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
)
{
    fun listForExchange(exchangeId: UUID, access: RequestAccessContext): List<InformationRequest>
    {
        exchangeRepository.findById(exchangeId)
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
        return visible
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

        exchangeRepository.findById(request.exchangeId)
            ?: throw IllegalStateException("Information Request $requestId has no parent Exchange")
        return request
    }
}
