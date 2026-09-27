package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsInputs
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsPage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsRow
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaClock
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.notification.DomainEventDeliveryStandingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

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
)
{
    @Transactional
    fun queue(filter: InformationRequestOperationsFilter): InformationRequestOperationsPage
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)
        val requests = requestRepository.findForOwner(owner.ownerType, owner.ownerId)
            .filter { filter.states.isEmpty() || it.state in filter.states }
            .filter { filter.exchangeId == null || it.exchangeId == filter.exchangeId }
        val inputs = inputsFor(requests)
        val now = clock.instant()
        val rows = requests.map { rowOf(it, inputs, now) }
            .filter { filter.slaStatuses.isEmpty() || it.standing.status in filter.slaStatuses }
            .filter { row -> filter.exceptions.isEmpty() || filter.exceptions.any { (row.exceptionCounts[it] ?: 0) > 0 } }
            .filter { !filter.exceptionsOnly || it.exceptionCounts.isNotEmpty() }
            .sortedWith(
                compareBy<InformationRequestOperationsRow, Instant?>(nullsLast()) { it.standing.nearestDueAt }
                    .thenBy { it.request.createdAt }
                    .thenBy { it.request.id },
            )
        return InformationRequestOperationsPage(rows.drop(filter.offset).take(filter.limit), rows.size, filter.limit, filter.offset)
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
        )
    }

    private fun rowOf(request: InformationRequest, inputs: InformationRequestOperationsInputs, now: Instant): InformationRequestOperationsRow
    {
        val requestClocks = inputs.clocks[request.id].orEmpty()
        val standing = InformationRequestSlaCalculator.standing(
            requestClocks.map { InformationRequestSlaClock(it, inputs.events[it.id].orEmpty(), dueSoonFrom(it, inputs.versions[it.policyVersionId])) },
            now,
        )
        val noticeCounts = inputs.notices[request.id].orEmpty().groupingBy { it }.eachCount()
        val delivery = inputs.deliveries[InformationRequestTransitionHistoryService.orderingKeyOf(request.id)]
        return InformationRequestOperationsRow(
            request = request,
            ageSeconds = ageOf(request, now),
            clockCount = requestClocks.size,
            standing = standing,
            noticeCounts = noticeCounts,
            exceptionCounts = mapOf(
                InformationRequestOperationsException.NOTICE_UNDELIVERABLE to (noticeCounts[InformationRequestNoticeDeliveryState.UNDELIVERABLE] ?: 0),
                InformationRequestOperationsException.NOTICE_FAILED to (noticeCounts[InformationRequestNoticeDeliveryState.FAILED] ?: 0),
                InformationRequestOperationsException.CLOCK_ESCALATED to standing.openEscalationCount,
                InformationRequestOperationsException.AUTOMATION_SKIPPED to (delivery?.skippedConsumptions ?: 0),
                InformationRequestOperationsException.EVENT_DELIVERY_FAILING to (delivery?.failingDeliveries ?: 0),
            ).filterValues { it > 0 },
        )
    }

    private fun dueSoonFrom(clock: InformationRequestClock, view: InformationRequestClockPolicyVersionView?): Instant?
    {
        if (clock.state != InformationRequestClockState.RUNNING || view == null) return null
        val earliest = view.reminderMinutesBeforeDue.maxOrNull() ?: return null
        return InformationRequestClockCalculator.retreat(view.calendar, clock.dueAt.toInstant(), earliest * SECONDS_PER_MINUTE)
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
    }
}
