package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestSummary
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestCallerStandingService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestSummaryAssembler @Inject constructor(
    private val titleReader: InformationRequestTitleReader,
    private val clockRepository: InformationRequestClockRepository,
    private val progressService: InformationRequestCompletenessProgressService,
    private val callerStanding: InformationRequestCallerStandingService,
    private val reviewQueryService: InformationRequestReviewQueryService,
    private val gate: InformationRequestMutationGate,
) {
    fun summarize(
        requests: List<InformationRequest>,
        access: RequestAccessContext,
        executionStandings: Map<UUID, InformationRequestExecutionStanding>,
    ): List<InformationRequestSummary> {
        if (requests.isEmpty()) return emptyList()
        val titles = titleReader.titlesOf(requests)
        val dueByRequest = clockRepository.findForRequests(requests.map { it.id })
            .filter { it.state == InformationRequestClockState.RUNNING }
            .groupBy { it.informationRequestId }
            .mapValues { entry -> entry.value.minOf { it.dueAt.toInstant() } }
        val reviewAwaited =
            reviewQueryService.queue(access, requests.map { it.id }.toSet()).map { it.request.id }.toSet()
        return requests.map { request ->
            val visibleItems = progressService.evaluate(request.id).items.filter { item ->
                item.requirementId?.let {
                    gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it)
                } ?: true
            }
            val caller = callerStanding.standingOf(request, access, reviewAwaited)
            val execution = executionStandings.getValue(request.id)
            InformationRequestSummary(
                request = request,
                title = titles.getValue(request.id),
                nextDueAt = dueByRequest[request.id].takeUnless { request.state.isTerminal },
                completedCount = visibleItems.count { it.contributesToNumerator },
                requiredCount = visibleItems.count { it.contributesToDenominator },
                standing = caller,
                executionStanding = if (caller.permissions.canManage) execution else execution.forParticipant(),
            )
        }
    }
}
