package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID

class InformationRequestParentLifecycleServiceTest
{
    private val repository = mock<InformationRequestRepository>()
    private val sessions = mock<RequestAccessSessionService>()
    private val history = mock<InformationRequestTransitionHistoryService>()
    private val service = InformationRequestParentLifecycleService(repository, sessions, history, mock())
    private val exchangeId = UUID.randomUUID()
    private val actor = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `rejection and rescission cancel unfinished requests exactly once and revoke all respondent sessions`()
    {
        for (status in listOf(ExchangeStatus.REJECTED, ExchangeStatus.RESCINDED))
        {
            reset(repository, sessions, history)
            val requests = InformationRequestState.entries.map { state ->
                InformationRequest().apply { this.exchangeId = this@InformationRequestParentLifecycleServiceTest.exchangeId; this.state = state }
            }
            whenever(repository.findForExchange(exchangeId)).thenReturn(requests)
            requests.forEach { whenever(repository.findRequestByIdForUpdate(it.id)).thenReturn(it) }
            service.apply(exchangeId, status, false, actor)
            service.apply(exchangeId, status, false, actor)
            requests.zip(InformationRequestState.entries).forEach { (request, original) ->
                assertEquals(if (original.isTerminal) original else InformationRequestState.CANCELLED, request.state)
                assertEquals(if (original.isTerminal) 1L else 2L, request.aggregateRevision)
                verify(sessions, times(2)).revokeAllForRequest(request.id)
                if (!original.isTerminal) assertNotNull(request.cancelledAt)
            }
            verify(history, times(InformationRequestState.entries.count { !it.isTerminal })).record(check {
                assertEquals(actor, it.actor)
                assertEquals("PARENT_${status.name}", it.reasonCode)
            })
        }
    }

    @Test
    fun `deletion retains request history and state while revoking sessions`()
    {
        val request = InformationRequest().apply { state = InformationRequestState.SUBMITTED }
        whenever(repository.findForExchange(exchangeId)).thenReturn(listOf(request))
        whenever(repository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        service.apply(exchangeId, ExchangeStatus.ACCEPTED_STARTED, true, actor)
        assertEquals(InformationRequestState.SUBMITTED, request.state)
        assertEquals(1L, request.aggregateRevision)
        verify(sessions).revokeAllForRequest(request.id)
        verifyNoInteractions(history)
        verify(repository, never()).update(any())
    }

    @Test
    fun `ending preserves request state and naturally expiring sessions`()
    {
        service.apply(exchangeId, ExchangeStatus.ENDED, false, actor)
        verifyNoInteractions(sessions, history)
    }
}
