package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestExchangeListing
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSummary
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestExchangeSummaryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val exchangeRepository: ExchangeRepository,
    private val titleReader: InformationRequestTitleReader,
    private val clockRepository: InformationRequestClockRepository,
    private val progressService: InformationRequestCompletenessProgressService,
    private val callerStanding: InformationRequestCallerStandingService,
    private val reviewQueryService: InformationRequestReviewQueryService,
    private val gate: InformationRequestMutationGate,
    private val authorizationService: AuthorizationService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
)
{
    fun listForExchange(exchangeId: UUID, access: RequestAccessContext): InformationRequestExchangeListing
    {
        val requests = queryService.listForExchange(exchangeId, access)
        val exchange = exchangeRepository.findById(exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")
        val titles = titleReader.titlesOf(requests)
        val dueByRequest = clockRepository.findForRequests(requests.map { it.id })
            .filter { it.state == InformationRequestClockState.RUNNING }
            .groupBy { it.informationRequestId }
            .mapValues { entry -> entry.value.minOf { it.dueAt.toInstant() } }
        val reviewAwaited = if (requests.isEmpty()) emptySet()
        else reviewQueryService.queue(access).map { it.request.id }.toSet()
        return InformationRequestExchangeListing(
            requests = requests.map { request ->
                val visibleItems = progressService.evaluate(request.id).items.filter { item ->
                    item.requirementId?.let {
                        gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it)
                    } ?: true
                }
                InformationRequestSummary(
                    request = request,
                    title = titles.getValue(request.id),
                    nextDueAt = dueByRequest[request.id],
                    completedCount = visibleItems.count { it.contributesToNumerator },
                    requiredCount = visibleItems.count { it.contributesToDenominator },
                    standing = callerStanding.standingOf(request, access, reviewAwaited),
                )
            },
            canCreate = canCreate(exchange, access),
        )
    }

    private fun canCreate(exchange: Exchange, access: RequestAccessContext): Boolean
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(exchange.id),
            access.authorization,
        )
        if (decision is Decision.Deny) return false
        val creation = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(status = exchange.status, deleted = exchange.isDeleted, lockedForUpdate = true),
            null,
            InformationRequestMutation.CREATE_DRAFT,
        )
        if (creation is InformationRequestPolicyDecision.Deny) return false
        return try
        {
            entitlementGuard.requireRequestMutation(exchange)
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
    }
}
