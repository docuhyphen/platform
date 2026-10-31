package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestAssignedWork
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestCallerPartyResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestAssignedWorkService @Inject constructor(
    private val callerParties: InformationRequestCallerPartyResolver,
    private val requestRepository: InformationRequestRepository,
    private val gate: InformationRequestMutationGate,
    private val exchangeLookup: ExchangeLookupService,
    private val summaries: InformationRequestSummaryAssembler,
    private val standingService: InformationRequestExecutionStandingService,
) {
    fun listFor(access: RequestAccessContext): List<InformationRequestAssignedWork> {
        val requests = callerParties.activePartiesActedFor(access.principal)
            .map { it.informationRequestId }
            .distinct()
            .mapNotNull(requestRepository::findById)
            .filterNot { it.state.isTerminal }
            .filter { gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, it.id) }
        val exchangeNames = requests.map { it.exchangeId }.distinct()
            .associateWith { exchangeLookup.findExchange(it)?.name }
        return summaries.summarize(requests, access, standingService.standingsOf(requests))
            .map { InformationRequestAssignedWork(it, exchangeNames[it.request.exchangeId]) }
            .sortedWith(compareBy<InformationRequestAssignedWork, java.time.Instant?>(nullsLast()) { it.summary.nextDueAt }
                .thenBy { it.summary.title })
    }
}
