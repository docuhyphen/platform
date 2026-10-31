package com.docuhyphen.app.api.service.informationrequest.oversight

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.oversight.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockCalculator
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestSlaCalculator
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeStateReader
import com.docuhyphen.app.api.service.notification.DomainEventDeliveryStandingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestOperationsService @Inject constructor(
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val requestRepository: InformationRequestRepository,
    private val clockRepository: InformationRequestClockRepository,
    private val clockEventRepository: InformationRequestClockEventRepository,
    private val policies: InformationRequestClockPolicyService,
    private val noticeStates: InformationRequestNoticeStateReader,
    private val deliveries: DomainEventDeliveryStandingService,
    private val clock: Clock,
    private val titleReader: InformationRequestTitleReader,
    private val partyRepository: InformationRequestPartyRepository,
    private val principalDisplayService: PrincipalDisplayService,
    private val standingService: InformationRequestExecutionStandingService,
)
{
    @Transactional
    fun queue(filter: InformationRequestOperationsFilter): InformationRequestOperationsPage
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)
        val criteria = InformationRequestOperationsCriteria(
            owner.ownerType,
            owner.ownerId,
            filter.states,
            filter.exchangeId,
            filter.search,
            filter.assigneeId,
        )
        val now = clock.instant()
        if (!filtersComputedValues(filter)) {
            val rows = rowsOf(requestRepository.findOperationsQueueIds(criteria, filter.limit, filter.offset), now)
            return InformationRequestOperationsPage(
                withAssignees(rows),
                requestRepository.countOperationsQueue(criteria),
                filter.limit,
                filter.offset,
            )
        }
        var total = 0
        val page = mutableListOf<InformationRequestOperationsRow>()
        requestRepository.findOperationsQueueIds(criteria, null, 0).chunked(COMPUTED_FILTER_CHUNK).forEach { chunk ->
            rowsOf(chunk, now).filter { matchesComputedFilters(it, filter) }.forEach { row ->
                if (total >= filter.offset && page.size < filter.limit) page += row
                total++
            }
        }
        return InformationRequestOperationsPage(withAssignees(page), total, filter.limit, filter.offset)
    }

    private fun filtersComputedValues(filter: InformationRequestOperationsFilter): Boolean =
        filter.slaStatuses.isNotEmpty() || filter.exceptions.isNotEmpty() || filter.exceptionsOnly

    private fun matchesComputedFilters(
        row: InformationRequestOperationsRow,
        filter: InformationRequestOperationsFilter
    ): Boolean =
        (filter.slaStatuses.isEmpty() || row.standing.status in filter.slaStatuses) &&
                (filter.exceptions.isEmpty() || filter.exceptions.any { (row.exceptionCounts[it] ?: 0) > 0 }) &&
                (!filter.exceptionsOnly || row.exceptionCounts.isNotEmpty())

    private fun rowsOf(requestIds: List<UUID>, now: Instant): List<InformationRequestOperationsRow> {
        val byId = requestRepository.findForIds(requestIds).associateBy { it.id }
        val requests = requestIds.mapNotNull(byId::get)
        val titles = titleReader.titlesOf(requests)
        val inputs = inputsFor(requests)
        return requests.map { rowOf(it, titles.getValue(it.id), inputs, now) }
    }

    private fun withAssignees(rows: List<InformationRequestOperationsRow>): List<InformationRequestOperationsRow> {
        val parties = partyRepository.findActiveForRequests(rows.map { it.request.id })
            .filter { it.principalKind != null && it.principalId != null }
            .groupBy { it.informationRequestId }
        return rows.map { row -> row.copy(assignees = assigneesOf(parties[row.request.id].orEmpty())) }
    }

    private fun assigneesOf(parties: List<InformationRequestParty>): List<InformationRequestOperationsAssignee> =
        parties.map { party ->
            val principal = PrincipalRef(requireNotNull(party.principalKind), requireNotNull(party.principalId))
            InformationRequestOperationsAssignee(
                roleKey = party.roleKey,
                principalKind = principal.kind,
                principalId = principal.id,
                label = principalDisplayService.display(principal).name,
            )
        }

    private fun inputsFor(requests: List<InformationRequest>): InformationRequestOperationsInputs
    {
        val requestIds = requests.map { it.id }
        val clocks = clockRepository.findForRequests(requestIds).groupBy { it.informationRequestId }
        val allClocks = clocks.values.flatten()
        return InformationRequestOperationsInputs(
            clocks = clocks,
            events = clockEventRepository.findForClocks(allClocks.map { it.id }).groupBy { it.clockId },
            versions = allClocks.map { it.policyVersionId }.distinct().associateWith { policies.versionView(it) },
            notices = noticeStates.statesForRequests(requestIds),
            deliveries = deliveries.standings(requestIds.map(InformationRequestTransitionHistoryService::orderingKeyOf)),
            executionStandings = standingService.standingsOf(requests),
        )
    }

    private fun rowOf(
        request: InformationRequest,
        title: String,
        inputs: InformationRequestOperationsInputs,
        now: Instant,
    ): InformationRequestOperationsRow
    {
        val requestClocks = inputs.clocks[request.id].orEmpty()
        val standing = InformationRequestSlaCalculator.standing(
            requestClocks.map {
                InformationRequestSlaClock(
                    it,
                    inputs.events[it.id].orEmpty(),
                    dueSoonFrom(it, inputs.versions[it.policyVersionId])
                )
            },
            now,
        )
        val noticeCounts = inputs.notices[request.id].orEmpty().groupingBy { it }.eachCount()
        val delivery = inputs.deliveries[InformationRequestTransitionHistoryService.orderingKeyOf(request.id)]
        return InformationRequestOperationsRow(
            request = request,
            title = title,
            assignees = emptyList(),
            ageSeconds = ageOf(request, now),
            clockCount = requestClocks.size,
            standing = standing,
            noticeCounts = noticeCounts,
            exceptionCounts = mapOf(
                InformationRequestOperationsException.NOTICE_UNDELIVERABLE to (noticeCounts[InformationRequestNoticeDeliveryState.UNDELIVERABLE]
                    ?: 0),
                InformationRequestOperationsException.NOTICE_FAILED to (noticeCounts[InformationRequestNoticeDeliveryState.FAILED]
                    ?: 0),
                InformationRequestOperationsException.CLOCK_ESCALATED to standing.openEscalationCount,
                InformationRequestOperationsException.AUTOMATION_SKIPPED to (delivery?.skippedConsumptions ?: 0),
                InformationRequestOperationsException.EVENT_DELIVERY_FAILING to (delivery?.failingDeliveries ?: 0),
            ).filterValues { it > 0 },
            executionStanding = inputs.executionStandings.getValue(request.id),
        )
    }

    private fun dueSoonFrom(clock: InformationRequestClock, view: InformationRequestClockPolicyVersionView?): Instant?
    {
        if (clock.state != InformationRequestClockState.RUNNING || view == null) return null
        val earliest = view.reminderMinutesBeforeDue.maxOrNull() ?: return null
        return InformationRequestClockCalculator.retreat(
            view.calendar,
            clock.dueAt.toInstant(),
            earliest * SECONDS_PER_MINUTE
        )
    }

    private fun ageOf(request: InformationRequest, now: Instant): Long
    {
        val from = (request.issuedAt ?: request.createdAt).toInstant()
        val until = listOfNotNull(request.closedAt, request.cancelledAt, request.supersededAt, request.expiredAt)
            .minOfOrNull { it.toInstant() }
            ?: now
        return Duration.between(from, until).seconds.coerceAtLeast(0)
    }

    private companion object
    {
        const val SECONDS_PER_MINUTE = 60L
        const val COMPUTED_FILTER_CHUNK = 500
    }
}
