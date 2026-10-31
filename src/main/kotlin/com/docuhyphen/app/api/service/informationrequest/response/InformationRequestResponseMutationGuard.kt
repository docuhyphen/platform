package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestResponseMutationGuard @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeLookup: ExchangeLookupService,
    private val continuationGuard: InformationRequestContinuationGuard,
) {
    fun lock(requestId: UUID, missingMessage: String): LockedInformationRequest {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeLookup)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, missingMessage)
        return LockedInformationRequest(exchange, request)
    }

    fun requireResponseChangeAllowed(locked: LockedInformationRequest, refusalMessage: String) {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = locked.exchange.status,
                deleted = locked.exchange.isDeleted,
                lockedForUpdate = true,
            ),
            locked.request.state,
            InformationRequestMutation.SAVE_RESPONSE,
        )
        if (decision is InformationRequestPolicyDecision.Deny) {
            throw InformationRequestLifecycleException(decision.reasonCode, refusalMessage)
        }
        continuationGuard.requireContinuation(locked, InformationRequestContinuationAction.RESPOND)
    }
}
