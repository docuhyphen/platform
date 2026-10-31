package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestResponseMutationGuardTest {
    private val requestRepository = mock<InformationRequestRepository>()
    private val exchangeLookup = mock<ExchangeLookupService>()
    private val continuationGuard = mock<InformationRequestContinuationGuard>()
    private val guard = InformationRequestResponseMutationGuard(requestRepository, exchangeLookup, continuationGuard)
    private val exchange = Exchange().apply {
        id = UUID.randomUUID()
        status = ExchangeStatus.ACCEPTED_STARTED
        isDeleted = false
    }
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = exchange.id
        state = InformationRequestState.IN_PROGRESS
    }

    init {
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        whenever(exchangeLookup.lockExchangeForUpdate(exchange.id)).thenReturn(exchange)
    }

    @Test
    fun `the parent Exchange is locked before the request row`() {
        assertEquals(
            LockedInformationRequest(exchange, request),
            guard.lock(request.id, "Information Request not found")
        )

        val locks = inOrder(exchangeLookup, requestRepository)
        locks.verify(exchangeLookup).lockExchangeForUpdate(exchange.id)
        locks.verify(requestRepository).findRequestByIdForUpdate(request.id)
    }

    @Test
    fun `a request row that cannot be locked is refused as not found in the caller's words`() {
        whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(null)

        val refusal = assertThrows<InformationRequestLifecycleException> {
            guard.lock(request.id, "Information Request receipt target not found")
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        assertEquals("Information Request receipt target not found", refusal.message)
    }

    @Test
    fun `a response change on an open request also answers to the continuation guard`() {
        val locked = LockedInformationRequest(exchange, request)

        guard.requireResponseChangeAllowed(locked, "Responses cannot change now")

        verify(continuationGuard).requireContinuation(locked, InformationRequestContinuationAction.RESPOND)
    }

    @Test
    fun `a response change under an ended Exchange is refused in the caller's words before any entitlement check`() {
        exchange.status = ExchangeStatus.ENDED

        val refusal = assertThrows<InformationRequestLifecycleException> {
            guard.requireResponseChangeAllowed(
                LockedInformationRequest(exchange, request),
                "Responses cannot change now"
            )
        }

        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, refusal.reasonCode)
        assertEquals("Responses cannot change now", refusal.message)
        verifyNoInteractions(continuationGuard)
    }
}
