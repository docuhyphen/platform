package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeKind
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestReminderResult
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InformationRequestReminderServiceTest
{
    private val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, UUID.randomUUID())
    private val actor = PrincipalRef.user(UUID.randomUUID())
    private val exchange = Exchange().apply {
        ownerOrganizationId = owner.ownerId
        status = ExchangeStatus.ACCEPTED_STARTED
    }
    private val ownerAccess = mock<InformationRequestOwnerScopeAccess>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val gate = mock<InformationRequestMutationGate>()
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val intentRepository = mock<InformationRequestNoticeIntentRepository>()
    private val transitionHistory = mock<InformationRequestTransitionHistoryService>()
    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val service = InformationRequestReminderService(
        ownerAccess, requestRepository, gate, partyRepository, intentRepository, transitionHistory,
        CommandReceiptService(InMemoryReminderCommandReceiptStore()),
        InformationRequestAbuseLimits(reminderCooldown = Duration.ofHours(24)),
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val transitions = mutableMapOf<UUID, InformationRequestTransition>()

    init
    {
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_SEND_REMINDERS)).thenReturn(actor)
        whenever(intentRepository.save(any())).thenAnswer { it.getArgument<InformationRequestNoticeIntent>(0) }
        whenever(transitionHistory.record(any())).thenAnswer {
            val command = it.getArgument<InformationRequestTransitionHistoryCommand>(0)
            InformationRequestTransition().apply { informationRequestId = command.request.id }.also { transitions[command.request.id] = it }
        }
    }

    @Test
    fun `each named open request is reminded once and each responding party is owed a reminder notice`()
    {
        val first = openRequest()
        val second = openRequest()
        val contributor = party(first, InformationRequestShareRoleKey.CONTRIBUTOR)
        val attestor = party(first, InformationRequestShareRoleKey.ATTESTOR)
        val reviewer = party(first, InformationRequestShareRoleKey.REVIEWER)
        val decisionMaker = party(first, InformationRequestShareRoleKey.DECISION_MAKER)
        val subjectOnly = party(first, InformationRequestShareRoleKey.SUBJECT).apply {
            principalKind = null
            principalId = null
            subjectIdentityRefId = UUID.randomUUID()
        }
        val preparer = party(second, InformationRequestShareRoleKey.PREPARER).apply { principalKind = PrincipalKind.PRINCIPAL_GROUP }
        whenever(partyRepository.findActiveForRequest(first.id)).thenReturn(listOf(contributor, attestor, reviewer, decisionMaker, subjectOnly))
        whenever(partyRepository.findActiveForRequest(second.id)).thenReturn(listOf(preparer))

        val results = service.send(SendInformationRequestRemindersCommand(listOf(first.id, second.id, first.id), "remind-1"))

        assertEquals(setOf(InformationRequestReminderResult(first.id, 2), InformationRequestReminderResult(second.id, 1)), results.toSet())
        val recorded = argumentCaptor<InformationRequestTransitionHistoryCommand>().also { verify(transitionHistory, times(2)).record(it.capture()) }.allValues
        recorded.forEach { command ->
            assertEquals(InformationRequestMutation.SEND_REMINDER, command.mutation)
            assertEquals(InformationRequestState.IN_PROGRESS, command.fromState)
            assertEquals(InformationRequestState.IN_PROGRESS, command.toState)
            assertEquals(actor, command.actor)
        }
        val intents = argumentCaptor<InformationRequestNoticeIntent>().also { verify(intentRepository, times(3)).save(it.capture()) }.allValues
        assertEquals(setOf(contributor.id, attestor.id, preparer.id), intents.map { it.partyId }.toSet())
        intents.forEach { intent ->
            assertEquals(InformationRequestNoticeKind.RESPONSE_REMINDER, intent.noticeKind)
            assertEquals(transitions.getValue(intent.informationRequestId).id, intent.transitionId)
            assertEquals(null, intent.clockEventId)
            assertEquals(null, intent.amendmentId)
        }
    }

    @Test
    fun `a retried Idempotency-Key replays each reminder without owing another notice`()
    {
        val request = openRequest()
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(listOf(party(request, InformationRequestShareRoleKey.CONTRIBUTOR)))

        val sent = service.send(SendInformationRequestRemindersCommand(listOf(request.id), "remind-once"))
        val replayed = service.send(SendInformationRequestRemindersCommand(listOf(request.id), "remind-once"))

        assertEquals(sent, replayed)
        verify(transitionHistory, times(1)).record(any())
        verify(intentRepository, times(1)).save(any())
    }

    @Test
    fun `a request reminded inside the cooldown is skipped and says when it can be reminded again`()
    {
        val request = openRequest()
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(listOf(party(request, InformationRequestShareRoleKey.CONTRIBUTOR)))
        whenever(transitionHistory.latestOccurrence(request.id, InformationRequestMutation.SEND_REMINDER))
            .thenReturn(now.minus(Duration.ofHours(1)))

        val results = service.send(SendInformationRequestRemindersCommand(listOf(request.id), "remind-again"))

        assertEquals(listOf(InformationRequestReminderResult(request.id, 0, now.plus(Duration.ofHours(23)))), results)
        verify(transitionHistory, never()).record(any())
        verify(intentRepository, never()).save(any())
    }

    @Test
    fun `a request is reminded again once its cooldown has passed`()
    {
        val request = openRequest()
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(listOf(party(request, InformationRequestShareRoleKey.CONTRIBUTOR)))
        whenever(transitionHistory.latestOccurrence(request.id, InformationRequestMutation.SEND_REMINDER))
            .thenReturn(now.minus(Duration.ofHours(25)))

        val results = service.send(SendInformationRequestRemindersCommand(listOf(request.id), "remind-later"))

        assertEquals(listOf(InformationRequestReminderResult(request.id, 1)), results)
    }

    @Test
    fun `another owner's request, work that is not open, and a caller who may not remind are refused`()
    {
        val foreign = openRequest().apply { ownerOrganizationId = UUID.randomUUID() }
        val closed = openRequest().apply { state = InformationRequestState.CLOSED }
        whenever(gate.requireMutation(eq(LockedInformationRequest(exchange, closed)), eq(InformationRequestMutation.SEND_REMINDER)))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.STATE_INVALID, "not open"))

        val notFound = assertThrows(InformationRequestLifecycleException::class.java) {
            service.send(SendInformationRequestRemindersCommand(listOf(foreign.id), "remind-foreign"))
        }
        val notOpen = assertThrows(InformationRequestLifecycleException::class.java) {
            service.send(SendInformationRequestRemindersCommand(listOf(closed.id), "remind-closed"))
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            service.send(SendInformationRequestRemindersCommand(emptyList(), "remind-none"))
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            service.send(SendInformationRequestRemindersCommand(List(101) { UUID.randomUUID() }, "remind-many"))
        }
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_SEND_REMINDERS)).thenThrow(ForbiddenException("denied"))
        assertThrows(ForbiddenException::class.java) {
            service.send(SendInformationRequestRemindersCommand(listOf(openRequest().id), "remind-denied"))
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, notFound.reasonCode)
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, notOpen.reasonCode)
        verify(transitionHistory, never()).record(any())
        verify(intentRepository, never()).save(any())
    }

    private fun openRequest(): InformationRequest
    {
        val request = InformationRequest().apply {
            exchangeId = exchange.id
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = owner.ownerId
            state = InformationRequestState.IN_PROGRESS
        }
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(gate.lock(request.id)).thenReturn(LockedInformationRequest(exchange, request))
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(emptyList())
        return request
    }

    private fun party(request: InformationRequest, role: InformationRequestShareRoleKey) = InformationRequestParty().apply {
        informationRequestId = request.id
        roleKey = role
        principalKind = PrincipalKind.USER
        principalId = UUID.randomUUID()
    }
}

private class InMemoryReminderCommandReceiptStore : CommandReceiptStore
{
    private val receipts = mutableListOf<CommandReceipt>()

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                it.resourceId == request.resource.id &&
                it.operationName == request.operation &&
                it.actorKind == request.actor.kind &&
                it.actorId == request.actor.id &&
                it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
