package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.noauth.RequestAccessSessionBinding
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockIssueRebaser
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestParentLifecycleServiceTest
{
    private val repository = mock<InformationRequestRepository>()
    private val sessions = mock<RequestAccessSessionService>()
    private val history = mock<InformationRequestTransitionHistoryService>()
    private val shares = mock<com.docuhyphen.app.api.service.exchange.ShareService>()
    private val bootstrapLinks = mock<InformationRequestBootstrapShareLinkService>()
    private val clockRebaser = mock<InformationRequestClockIssueRebaser>()
    private val workflows = mock<WorkflowEngineService>()
    private val service =
        InformationRequestParentLifecycleService(
            repository,
            sessions,
            history,
            shares,
            bootstrapLinks,
            mock(),
            clockRebaser,
            workflows
        )
    private val exchangeId = UUID.randomUUID()
    private val actor = InformationRequestAuditActor.of(
        RequestAccessContext(
            PrincipalRef.user(UUID.randomUUID()),
            AuthorizationContext(sessionRef = "exchange-owner-session")
        ),
    )

    @Test
    fun `rejection and rescission cancel unfinished requests exactly once and revoke all respondent sessions`()
    {
        for (status in listOf(ExchangeStatus.REJECTED, ExchangeStatus.RESCINDED))
        {
            reset(repository, sessions, history)
            val requests = InformationRequestState.entries.map { state ->
                InformationRequest().apply {
                    this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId; this.state = state
                }
            }
            whenever(repository.findForExchange(exchangeId)).thenReturn(requests)
            requests.forEach { whenever(repository.findRequestByIdForUpdate(it.id)).thenReturn(it) }
            service.apply(exchangeId, status, false, actor)
            service.apply(exchangeId, status, false, actor)
            requests.zip(InformationRequestState.entries).forEach { (request, original) ->
                assertEquals(if (original.isTerminal) original else InformationRequestState.CANCELLED, request.state)
                assertEquals(if (original.isTerminal) 1L else 2L, request.aggregateRevision)
                verify(sessions, times(2)).revokeLiveForRequest(request.id)
                if (!original.isTerminal) assertNotNull(request.cancelledAt)
            }
            verify(history, times(InformationRequestState.entries.count { !it.isTerminal })).record(check {
                assertEquals(actor, it.actor)
                assertEquals("PARENT_${status.name}", it.reasonCode)
            })
        }
    }

    @Test
    fun `every request keeps its Exchange owners' reads whether an organization or a person owns it`() {
        val organizationRequest = InformationRequest().apply {
            this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId
            ownerType = com.docuhyphen.app.api.model.entity.InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        val personalRequest = InformationRequest().apply {
            this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId
            ownerType = com.docuhyphen.app.api.model.entity.InformationRequestOwnerType.USER
            ownerUserId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(organizationRequest, personalRequest))
        whenever(repository.findRequestByIdForUpdate(organizationRequest.id)).thenReturn(organizationRequest)
        whenever(repository.findRequestByIdForUpdate(personalRequest.id)).thenReturn(personalRequest)

        service.apply(exchangeId, ExchangeStatus.REJECTED, false, actor)

        verify(shares).retainInformationRequestOwnerReads(exchangeId, organizationRequest.id)
        verify(shares).retainInformationRequestOwnerReads(exchangeId, personalRequest.id)
    }

    @Test
    fun `each revoked session is recorded with its party and the parent cause before the request is cancelled`()
    {
        val request = InformationRequest().apply {
            this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId; state =
            InformationRequestState.IN_PROGRESS
        }
        val partyId = UUID.randomUUID()
        val session = RequestAccessSession().apply { shareLinkId = UUID.randomUUID() }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(request))
        whenever(repository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        whenever(sessions.revokeLiveForRequest(request.id)).thenReturn(
            listOf(
                RequestAccessSessionBinding(
                    session,
                    partyId
                )
            )
        )

        service.apply(exchangeId, ExchangeStatus.RESCINDED, false, actor)

        val recorded =
            argumentCaptor<com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand>()
        verify(history, times(2)).record(recorded.capture())
        val cutoff = recorded.firstValue
        assertEquals(InformationRequestMutation.REVOKE_ACCESS_SESSION, cutoff.mutation)
        assertEquals(InformationRequestState.IN_PROGRESS, cutoff.fromState)
        assertEquals(InformationRequestState.IN_PROGRESS, cutoff.toState)
        assertEquals(actor, cutoff.actor)
        assertEquals("PARENT_RESCINDED", cutoff.reasonCode)
        assertEquals(partyId, cutoff.partyId)
        assertTrue(cutoff.requireAuditCapture)
        assertNotEquals(session.id.toString(), cutoff.actor.sessionRef)
        assertEquals(
            "information_request.revoke_access_session|${request.id}|${session.id}",
            cutoff.auditIdempotencyKey
        )
        assertEquals(
            mapOf("accessSessionId" to session.id.toString(), "shareLinkId" to session.shareLinkId.toString()),
            cutoff.details
        )
        assertEquals(InformationRequestMutation.CANCEL, recorded.secondValue.mutation)
    }

    @Test
    fun `rejection, rescission and deletion end the workflows running on every request, finished or not`() {
        listOf(
            ExchangeStatus.REJECTED to false,
            ExchangeStatus.RESCINDED to false,
            ExchangeStatus.ACCEPTED_STARTED to true
        )
            .forEach { (status, deleted) ->
                reset(workflows)
                val open = InformationRequest().apply { state = InformationRequestState.IN_PROGRESS }
                val finished = InformationRequest().apply { state = InformationRequestState.CLOSED }
                whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(open, finished))
                whenever(repository.findRequestByIdForUpdate(open.id)).thenReturn(open)
                whenever(repository.findRequestByIdForUpdate(finished.id)).thenReturn(finished)

                service.apply(exchangeId, status, deleted, actor)

                val cause = if (deleted) "PARENT_DELETED" else "PARENT_${status.name}"
                verify(workflows).cancelActiveForSubject("INFORMATION_REQUEST", open.id, cause)
                verify(workflows).cancelActiveForSubject("INFORMATION_REQUEST", finished.id, cause)
            }
    }

    @Test
    fun `ending an Exchange leaves the workflows of its requests running`() {
        val finished = InformationRequest().apply { state = InformationRequestState.CLOSED }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(finished))
        whenever(repository.findRequestByIdForUpdate(finished.id)).thenReturn(finished)

        service.apply(exchangeId, ExchangeStatus.ENDED, false, actor)

        verify(workflows, never()).cancelActiveForSubject(any(), any(), anyOrNull())
    }

    @Test
    fun `deletion cancels an unfinished request with the deletion cause and leaves a finished one alone`() {
        val open = InformationRequest().apply { state = InformationRequestState.IN_PROGRESS }
        val finished = InformationRequest().apply { state = InformationRequestState.CLOSED }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(open, finished))
        whenever(repository.findRequestByIdForUpdate(open.id)).thenReturn(open)
        whenever(repository.findRequestByIdForUpdate(finished.id)).thenReturn(finished)

        service.apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, true, actor)

        assertEquals(InformationRequestState.CANCELLED, open.state)
        assertEquals(2L, open.aggregateRevision)
        assertNotNull(open.cancelledAt)
        assertEquals(InformationRequestState.CLOSED, finished.state)
        verify(sessions).revokeLiveForRequest(open.id)
        verify(repository).update(open)
        verify(repository, never()).update(finished)
        verify(history).record(check {
            assertEquals(InformationRequestMutation.CANCEL, it.mutation)
            assertEquals("PARENT_DELETED", it.reasonCode)
        })
    }

    @Test
    fun `deleting the parent records a live session cutoff with the deletion cause`() {
        val request = InformationRequest().apply { state = InformationRequestState.ISSUED }
        val session = RequestAccessSession().apply { shareLinkId = UUID.randomUUID() }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(request))
        whenever(repository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        whenever(sessions.revokeLiveForRequest(request.id)).thenReturn(
            listOf(
                RequestAccessSessionBinding(
                    session,
                    null
                )
            )
        )

        service.apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, true, actor)

        val recorded =
            argumentCaptor<com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand>()
        verify(history, times(2)).record(recorded.capture())
        assertEquals(InformationRequestMutation.REVOKE_ACCESS_SESSION, recorded.firstValue.mutation)
        assertEquals("PARENT_DELETED", recorded.firstValue.reasonCode)
        assertNull(recorded.firstValue.partyId)
        assertEquals(InformationRequestMutation.CANCEL, recorded.secondValue.mutation)
        assertEquals("PARENT_DELETED", recorded.secondValue.reasonCode)
        assertEquals(InformationRequestState.CANCELLED, request.state)
    }

    @Test
    fun `rejection, rescission and deletion revoke every access link of each request after its sessions`()
    {
        listOf(
            Triple(ExchangeStatus.REJECTED, false, "PARENT_REJECTED"),
            Triple(ExchangeStatus.RESCINDED, false, "PARENT_RESCINDED"),
            Triple(ExchangeStatus.ACCEPTED_STARTED, true, "PARENT_DELETED"),
        ).forEach { (status, deleted, reason) ->
            reset(repository, sessions, history, bootstrapLinks)
            val request = InformationRequest().apply {
                this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId
                state = InformationRequestState.IN_PROGRESS
            }
            whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(request))
            whenever(repository.findRequestByIdForUpdate(request.id)).thenReturn(request)

            service.apply(exchangeId, status, deleted, actor)

            val order = inOrder(sessions, bootstrapLinks)
            order.verify(sessions).revokeLiveForRequest(request.id)
            order.verify(bootstrapLinks)
                .revokeLinksKeepingSessions(request, InformationRequestCredentialCutoff(actor, reason))
        }
    }

    @Test
    fun `accepting an Exchange counts the clocks of its issued requests from the acceptance, once`() {
        val issued = InformationRequest().apply { state = InformationRequestState.ISSUED }
        val draft = InformationRequest().apply { state = InformationRequestState.DRAFT }
        val closed = InformationRequest().apply { state = InformationRequestState.CLOSED }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(issued, draft, closed))
        listOf(issued, draft, closed).forEach { whenever(repository.findRequestByIdForUpdate(it.id)).thenReturn(it) }

        service.apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, false, actor, ExchangeStatus.INITIATED)
        service.apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, false, actor, ExchangeStatus.ACCEPTED_STARTED)
        service.apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, false, actor)

        verify(clockRebaser).rebaseOnAcceptance(eq(issued), any(), eq(actor.principal))
        verify(clockRebaser, never()).rebaseOnAcceptance(eq(draft), any(), any())
        verify(clockRebaser, never()).rebaseOnAcceptance(eq(closed), any(), any())
        verifyNoInteractions(sessions, history, bootstrapLinks)
        assertEquals(InformationRequestState.ISSUED, issued.state)
    }

    @Test
    fun `ending preserves request state, naturally expiring sessions and access links`() {
        val request = InformationRequest().apply {
            this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId
            state = InformationRequestState.ISSUED
        }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(request))
        whenever(repository.findRequestByIdForUpdate(request.id)).thenReturn(request)

        service.apply(exchangeId, ExchangeStatus.ENDED, false, actor)

        assertEquals(InformationRequestState.ISSUED, request.state)
        verifyNoInteractions(sessions, history, bootstrapLinks)
    }
}
