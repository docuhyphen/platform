package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestExchangeListing
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestExchangeSummaryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val exchangeLookup: ExchangeLookupService,
    private val summaries: InformationRequestSummaryAssembler,
    private val authorizationService: AuthorizationService,
    private val standingService: InformationRequestExecutionStandingService,
)
{
    fun listForExchange(exchangeId: UUID, access: RequestAccessContext): InformationRequestExchangeListing
    {
        val requests = queryService.listForExchange(exchangeId, access)
        val exchange = exchangeLookup.findExchange(exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")
        val owner = standingService.ownerStanding(exchange)
        val creation = if (creationPermitted(exchange, access)) standingService.creationStanding(exchange) else null
        return InformationRequestExchangeListing(
            requests = summaries.summarize(
                requests,
                access,
                requests.associate { it.id to standingService.standingOf(it, owner) },
            ),
            canCreate = creation?.available == true,
            creationUnavailableReason = creation?.unavailableReason,
            creationOwner = creation?.let { InformationRequestOwnerRef.of(exchange) },
        )
    }

    private fun creationPermitted(exchange: Exchange, access: RequestAccessContext): Boolean
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(exchange.id),
            access.authorization,
        )
        if (decision is Decision.Deny) return false
        val creation = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true
            ),
            null,
            InformationRequestMutation.CREATE_DRAFT,
        )
        return creation !is InformationRequestPolicyDecision.Deny
    }
}
