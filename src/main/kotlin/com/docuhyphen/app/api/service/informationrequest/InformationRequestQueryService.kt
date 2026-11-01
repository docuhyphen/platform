package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestQueryService @Inject constructor(
    private val exchangeLookup: ExchangeLookupService,
    private val requestRepository: InformationRequestRepository,
    private val authorizationService: AuthorizationService,
)
{
    fun listForExchange(exchangeId: UUID, access: RequestAccessContext): List<InformationRequest>
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.exchange(exchangeId),
            access.authorization,
        )
        val visible = requestRepository.findForExchange(exchangeId).filter {
            authorizationService.authorize(
                access.principal, Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(it.id), access.authorization
            ) !is Decision.Deny
        }
        if (decision is Decision.Deny && visible.isEmpty())
        {
            throw ForbiddenException("Access denied to list Information Requests")
        }
        exchangeLookup.findExchange(exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")
        return visible
    }

    fun ownerOf(requestId: UUID): InformationRequestOwnerRef? =
        requestRepository.findById(requestId)?.let(InformationRequestOwnerRef::of)

    fun findById(requestId: UUID, access: RequestAccessContext): InformationRequest
    {
        InformationRequestReadAuthorization.requireView(authorizationService, requestId, access)
        val request = requestRepository.findById(requestId)
            ?: throw IllegalArgumentException("Information Request not found")

        exchangeLookup.findExchange(request.exchangeId)
            ?: throw IllegalStateException("Information Request $requestId has no parent Exchange")
        return request
    }
}
