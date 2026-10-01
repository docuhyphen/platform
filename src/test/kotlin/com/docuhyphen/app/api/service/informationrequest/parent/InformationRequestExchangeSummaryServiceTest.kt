package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestClock
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestOwnerStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestCallerStanding
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestNextAction
import com.docuhyphen.app.api.model.informationrequest.parent.InformationRequestSummaryPermissions
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestProgressProjection
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewQueueEntry
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestCallerStandingService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestExchangeSummaryServiceTest
{
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val exchange = Exchange().apply {
        ownerOrganizationId = UUID.randomUUID()
        status = ExchangeStatus.ACCEPTED_STARTED
    }
    private val queryService = mock<InformationRequestQueryService>()
    private val exchangeRepository = mock<ExchangeRepository>()
    private val titleReader = mock<InformationRequestTitleReader>()
    private val clockRepository = mock<InformationRequestClockRepository>()
    private val progressService = mock<InformationRequestCompletenessProgressService>()
    private val callerStanding = mock<InformationRequestCallerStandingService>()
    private val reviewQueryService = mock<InformationRequestReviewQueryService>()
    private val gate = mock<InformationRequestMutationGate>()
    private val authorizationService = mock<AuthorizationService>()
    private val standingService = mock<InformationRequestExecutionStandingService>()
    private val service = InformationRequestExchangeSummaryService(
        queryService, exchangeRepository, titleReader, clockRepository, progressService, callerStanding,
        reviewQueryService, gate, authorizationService, standingService,
    )
    private val continuing = InformationRequestExecutionStanding(
        InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
        InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE,
    )
    private val standing = InformationRequestCallerStanding(
        roles = listOf(InformationRequestShareRoleKey.CONTRIBUTOR),
        permissions = InformationRequestSummaryPermissions(canManage = false, canRespond = true, canReview = false),
        nextAction = InformationRequestNextAction.RESPOND,
    )

    init
    {
        whenever(exchangeRepository.findById(exchange.id)).thenReturn(exchange)
        whenever(reviewQueryService.queue(access)).thenReturn(emptyList())
        whenever(clockRepository.findForRequests(any())).thenReturn(emptyList())
        whenever(authorizationService.authorize(any(), any(), any<ResourceRef>(), any())).thenReturn(Decision.Allow())
        whenever(standingService.ownerStanding(exchange)).thenReturn(owner(null))
        whenever(standingService.standingOf(any(), any())).thenReturn(continuing)
    }

    @Test
    fun `each request the caller may see is summarized with its title, nearest running due instant, progress, and standing`()
    {
        val issued = request(InformationRequestState.ISSUED).apply { issuedAt = Timestamp.from(START) }
        val draft = request(InformationRequestState.DRAFT)
        val visibleDone = UUID.randomUUID()
        val visibleOpen = UUID.randomUUID()
        val hiddenDone = UUID.randomUUID()
        whenever(queryService.listForExchange(exchange.id, access)).thenReturn(listOf(issued, draft))
        whenever(titleReader.titlesOf(listOf(issued, draft))).thenReturn(mapOf(issued.id to "Periodic records request", draft.id to "Draft request"))
        whenever(clockRepository.findForRequests(any())).thenReturn(
            listOf(
                clock(issued, InformationRequestClockState.RUNNING, START.plusSeconds(7200)),
                clock(issued, InformationRequestClockState.RUNNING, START.plusSeconds(3600)),
                clock(issued, InformationRequestClockState.PAUSED, START.plusSeconds(600)),
                clock(draft, InformationRequestClockState.STOPPED, START.plusSeconds(60)),
            ),
        )
        whenever(progressService.evaluate(issued.id)).thenReturn(
            progress(
                item(visibleDone, InformationRequestCompletenessItemState.COMPLETE, counted = true, done = true),
                item(visibleOpen, InformationRequestCompletenessItemState.INCOMPLETE, counted = true, done = false),
                item(hiddenDone, InformationRequestCompletenessItemState.COMPLETE, counted = true, done = true),
                item(null, InformationRequestCompletenessItemState.COMPLETE, counted = true, done = true),
            ),
        )
        whenever(progressService.evaluate(draft.id)).thenReturn(progress())
        whenever(gate.permitsRequirement(eq(access), eq(Action.INFORMATION_REQUEST_REQUIREMENT_VIEW), any())).thenReturn(true)
        whenever(gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, hiddenDone)).thenReturn(false)
        whenever(callerStanding.standingOf(any(), eq(access), any())).thenReturn(standing)

        val listing = service.listForExchange(exchange.id, access)

        assertEquals(listOf(issued.id, draft.id), listing.requests.map { it.request.id })
        val summary = listing.requests.first()
        assertEquals("Periodic records request", summary.title)
        assertEquals(START.plusSeconds(3600), summary.nextDueAt)
        assertEquals(2, summary.completedCount)
        assertEquals(3, summary.requiredCount)
        assertEquals(standing, summary.standing)
        assertEquals(InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE), summary.executionStanding)
        assertEquals(null, listing.requests.last().nextDueAt)
        assertEquals(0, listing.requests.last().requiredCount)
    }

    @Test
    fun `a manager sees why a request continues after a lapse while a party only sees whether it can act`()
    {
        val issued = request(InformationRequestState.IN_PROGRESS)
        val suspended = request(InformationRequestState.IN_PROGRESS)
        whenever(queryService.listForExchange(exchange.id, access)).thenReturn(listOf(issued, suspended))
        whenever(titleReader.titlesOf(listOf(issued, suspended))).thenReturn(mapOf(issued.id to "Records", suspended.id to "Records"))
        whenever(progressService.evaluate(any())).thenReturn(progress())
        val managing = standing.copy(permissions = standing.permissions.copy(canManage = true))
        whenever(callerStanding.standingOf(eq(issued), eq(access), any())).thenReturn(managing)
        whenever(callerStanding.standingOf(eq(suspended), eq(access), any())).thenReturn(standing)
        whenever(standingService.standingOf(eq(suspended), any())).thenReturn(
            InformationRequestExecutionStanding(
                InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
                InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
            ),
        )

        val summaries = service.listForExchange(exchange.id, access).requests

        assertEquals(continuing, summaries.first().executionStanding)
        assertEquals(
            InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED),
            summaries.last().executionStanding,
        )
    }

    @Test
    fun `the caller's awaited reviews reach its standing`()
    {
        val issued = request(InformationRequestState.IN_PROGRESS)
        whenever(queryService.listForExchange(exchange.id, access)).thenReturn(listOf(issued))
        whenever(titleReader.titlesOf(listOf(issued))).thenReturn(mapOf(issued.id to "Periodic records request"))
        whenever(progressService.evaluate(issued.id)).thenReturn(progress())
        whenever(reviewQueryService.queue(access)).thenReturn(
            listOf(
                InformationRequestReviewQueueEntry(
                    assignment = InformationRequestReviewAssignment(),
                    review = InformationRequestReview(),
                    request = issued,
                    packageNumber = 1,
                    stageKey = null,
                    itemCount = 1,
                ),
            ),
        )
        whenever(callerStanding.standingOf(issued, access, setOf(issued.id))).thenReturn(standing)

        val listing = service.listForExchange(exchange.id, access)

        assertEquals(standing, listing.requests.single().standing)
    }

    @Test
    fun `creation is offered only when the caller may create, the Exchange takes new requests, and the owner's plan allows it`()
    {
        whenever(queryService.listForExchange(exchange.id, access)).thenReturn(emptyList())
        whenever(titleReader.titlesOf(emptyList())).thenReturn(emptyMap())

        val offered = service.listForExchange(exchange.id, access).canCreate
        whenever(authorizationService.authorize(access.principal, Action.INFORMATION_REQUEST_CREATE, ResourceRef.exchange(exchange.id), access.authorization))
            .thenReturn(Decision.Deny("DENIED", "denied"))
        val denied = service.listForExchange(exchange.id, access).canCreate
        whenever(authorizationService.authorize(access.principal, Action.INFORMATION_REQUEST_CREATE, ResourceRef.exchange(exchange.id), access.authorization))
            .thenReturn(Decision.Allow())
        whenever(standingService.ownerStanding(exchange)).thenReturn(owner(InformationRequestStandingReason.FEATURE_NOT_INCLUDED))
        val lapsed = service.listForExchange(exchange.id, access)
        whenever(standingService.ownerStanding(exchange)).thenReturn(owner(null))
        exchange.status = ExchangeStatus.ENDED
        val ended = service.listForExchange(exchange.id, access)

        assertEquals(true, offered)
        assertEquals(false, denied)
        assertEquals(false, lapsed.canCreate)
        assertEquals(InformationRequestStandingReason.FEATURE_NOT_INCLUDED, lapsed.creationUnavailableReason)
        assertEquals(false, ended.canCreate)
        assertEquals(null, ended.creationUnavailableReason)
        assertEquals(emptyList<Any>(), service.listForExchange(exchange.id, access).requests)
    }

    private fun request(requestState: InformationRequestState) = InformationRequest().apply {
        exchangeId = exchange.id
        templateVersionId = UUID.randomUUID()
        state = requestState
        createdAt = Timestamp.from(START)
    }

    private fun clock(request: InformationRequest, clockState: InformationRequestClockState, due: Instant) =
        InformationRequestClock().apply {
            informationRequestId = request.id
            state = clockState
            dueAt = Timestamp.from(due)
        }

    private fun item(requirementId: UUID?, itemState: InformationRequestCompletenessItemState, counted: Boolean, done: Boolean) =
        InformationRequestCompletenessContribution(
            itemKey = "item-${requirementId ?: "request"}",
            requirementId = requirementId,
            state = itemState,
            contributesToDenominator = counted,
            contributesToNumerator = done,
        )

    private fun progress(vararg items: InformationRequestCompletenessContribution) = InformationRequestProgressProjection(
        completedCount = items.count { it.contributesToNumerator },
        totalCount = items.count { it.contributesToDenominator },
        percentComplete = 0,
        items = items.toList(),
    )

    private fun owner(reason: InformationRequestStandingReason?) = InformationRequestOwnerStanding(
        ownerType = SubscriptionOwnerType.ORGANIZATION,
        ownerId = exchange.ownerOrganizationId!!,
        planCode = PlanCode.BUSINESS,
        status = SubscriptionStatus.ACTIVE,
        enforcementMode = SubscriptionEnforcementMode.ENFORCE,
        featureIncluded = reason == null,
        operationallySuspended = false,
        newWorkUnavailableReason = reason,
    )

    private companion object
    {
        val START: Instant = Instant.parse("2026-09-27T08:00:00Z")
    }
}
