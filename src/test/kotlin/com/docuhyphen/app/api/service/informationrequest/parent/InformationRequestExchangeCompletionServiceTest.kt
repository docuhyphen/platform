package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

class InformationRequestExchangeCompletionServiceTest {
    @Test
    fun `a request cancelled as its Exchange ends is recorded only with a captured audit record`() {
        val requests = mock<InformationRequestRepository>()
        val history = mock<InformationRequestTransitionHistoryService>()
        val exchange = Exchange().apply { status = ExchangeStatus.ACCEPTED_STARTED }
        val remaining = InformationRequest().apply {
            exchangeId = exchange.id
            state = InformationRequestState.ISSUED
            gatesExchangeClosure = false
        }
        whenever(requests.findForExchange(exchange.id)).thenReturn(listOf(remaining))
        whenever(requests.findRequestByIdForUpdate(remaining.id)).thenReturn(remaining)
        val actor = InformationRequestAuditActor.of(
            RequestAccessContext(
                PrincipalRef.user(UUID.randomUUID()),
                AuthorizationContext(sessionRef = "ending-session")
            ),
        )

        InformationRequestExchangeCompletionService(
            requestRepository = requests,
            transitionHistory = history,
            clockStopper = mock(),
            bootstrapLinks = mock(),
            gate = mock(),
            clock = Clock.systemUTC(),
        )
            .prepareEnding(exchange, actor, cancelRemaining = true)

        verify(history).record(check {
            assertEquals(InformationRequestMutation.CANCEL, it.mutation)
            assertTrue(it.requireAuditCapture)
        })
    }

    @Test
    fun `an ending confirmed later cancels no request created after its cancel authority was checked`() {
        val requests = mock<InformationRequestRepository>()
        val history = mock<InformationRequestTransitionHistoryService>()
        val exchange = Exchange().apply { status = ExchangeStatus.ACCEPTED_STARTED }
        val checkedAt = Instant.parse("2026-10-29T08:00:00Z")
        val covered = remaining(exchange, checkedAt.minusSeconds(60))
        val later = remaining(exchange, checkedAt.plusSeconds(60))
        whenever(requests.findForExchange(exchange.id)).thenReturn(listOf(covered, later))
        listOf(covered, later).forEach { whenever(requests.findRequestByIdForUpdate(it.id)).thenReturn(it) }
        val actor = InformationRequestAuditActor.of(
            RequestAccessContext(
                PrincipalRef.user(UUID.randomUUID()),
                AuthorizationContext(sessionRef = "ending-session")
            ),
        )

        val refusal = assertThrows<InformationRequestExchangeCompletionException> {
            InformationRequestExchangeCompletionService(
                requestRepository = requests,
                transitionHistory = history,
                clockStopper = mock(),
                bootstrapLinks = mock(),
                gate = mock(),
                clock = Clock.systemUTC(),
            )
                .prepareEnding(exchange, actor, cancelRemaining = true, cancelAuthorityCheckedAt = checkedAt)
        }

        assertEquals(InformationRequestErrorCatalog.REMAINING_REQUESTS_NOT_CANCELLABLE, refusal.reasonCode)
        assertEquals(setOf(covered.id, later.id), refusal.requestIds.toSet())
        verify(history, never()).record(any())
    }

    private fun remaining(exchange: Exchange, created: Instant) = InformationRequest().apply {
        exchangeId = exchange.id
        state = InformationRequestState.ISSUED
        gatesExchangeClosure = false
        createdAt = Timestamp.from(created)
    }
}
